# Phase 6 — Testing

## References

- `copilot/architecture.md` — port interfaces enable test doubles; ACL boundary enables WireMock isolation
- `copilot/battle-plan.md` — unit tests for use case rules; integration tests for endpoints + WireMock
- `copilot/challenge.md` — business rules to cover: AGRO validation, PJ record, eligibility check
- `copilot/design.md` — LSP: adapters implement ports correctly and are swappable with test doubles
- `copilot/hurdles.md` — non-negotiable rules (no commits)

---

## Goal

Two layers of tests:

1. **Unit tests** — use case logic in complete isolation. No Spring context. All out-ports mocked.
2. **Integration tests** — full Spring context + real PostgreSQL (Testcontainers) + WireMock for the external product service.

---

## Step 0 — pom.xml: add test dependencies

```xml
<!-- Testcontainers -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- WireMock -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.4.2</version>
    <scope>test</scope>
</dependency>
```

> `spring-boot-starter-test` already provides JUnit 5 and Mockito — no additional dependency needed for unit tests.

---

## Step 1 — Test application profile

**File:** `src/test/resources/application-test.yml`

Overrides `ddl-auto: validate` (production setting) with `create-drop` so Hibernate
generates the schema from JPA entities during integration tests.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
```

---

## Step 2 — Unit test: ContratarOperacaoCreditoServiceTest

**File:** `src/test/java/br/com/sicredi/creditservice/application/usecase/ContratarOperacaoCreditoServiceTest.java`

Tests all business rules in isolation. No Spring context. Ports mocked with Mockito.

```java
package br.com.sicredi.creditservice.application.usecase;

import br.com.sicredi.creditservice.domain.exception.AgroSemAreaException;
import br.com.sicredi.creditservice.domain.exception.ElegibilidadeNegadaException;
import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.in.ContratoOperacaoCommand;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import br.com.sicredi.creditservice.domain.port.out.OperacaoSocioRepository;
import br.com.sicredi.creditservice.domain.port.out.ProdutoElegibilidadePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContratarOperacaoCreditoServiceTest {

    @Mock OperacaoCreditoRepository operacaoCreditoRepository;
    @Mock OperacaoSocioRepository operacaoSocioRepository;
    @Mock ProdutoElegibilidadePort produtoElegibilidadePort;

    ContratarOperacaoCreditoService service;

    @BeforeEach
    void setUp() {
        service = new ContratarOperacaoCreditoService(
                operacaoCreditoRepository, operacaoSocioRepository, produtoElegibilidadePort);
    }

    @Test
    void shouldThrowWhenAgroHasNullArea() {
        ContratoOperacaoCommand command = agroCommand(null);

        assertThatThrownBy(() -> service.executar(command))
                .isInstanceOf(AgroSemAreaException.class);

        verify(produtoElegibilidadePort, never()).verificar(any(), any(), any());
        verify(operacaoCreditoRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAgroHasZeroArea() {
        ContratoOperacaoCommand command = agroCommand(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.executar(command))
                .isInstanceOf(AgroSemAreaException.class);

        verify(produtoElegibilidadePort, never()).verificar(any(), any(), any());
    }

    @Test
    void shouldThrowWhenEligibilityDenied() {
        ContratoOperacaoCommand command = pfCommand();
        when(produtoElegibilidadePort.verificar(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.executar(command))
                .isInstanceOf(ElegibilidadeNegadaException.class);

        verify(operacaoCreditoRepository, never()).save(any());
    }

    @Test
    void shouldContractPfOperacaoWithoutSocioRecord() {
        ContratoOperacaoCommand command = pfCommand();
        when(produtoElegibilidadePort.verificar(any(), any(), any())).thenReturn(true);
        when(operacaoCreditoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = service.executar(command);

        assertThat(id).isNotNull();
        verify(operacaoSocioRepository, never()).save(any());
    }

    @Test
    void shouldContractPjOperacaoAndPersistSocioRecord() {
        ContratoOperacaoCommand command = pjCommand();
        when(produtoElegibilidadePort.verificar(any(), any(), any())).thenReturn(true);
        when(operacaoCreditoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = service.executar(command);

        assertThat(id).isNotNull();
        ArgumentCaptor<br.com.sicredi.creditservice.domain.model.OperacaoSocio> captor =
                ArgumentCaptor.forClass(br.com.sicredi.creditservice.domain.model.OperacaoSocio.class);
        verify(operacaoSocioRepository).save(captor.capture());
        assertThat(captor.getValue().idOperacaoCredito()).isEqualTo(id);
        assertThat(captor.getValue().idAssociado()).isEqualTo(command.idAssociado());
    }

    @Test
    void shouldContractAgroOperacaoWhenAreaIsValid() {
        ContratoOperacaoCommand command = agroCommand(new BigDecimal("10.5"));
        when(produtoElegibilidadePort.verificar(any(), any(), any())).thenReturn(true);
        when(operacaoCreditoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = service.executar(command);

        assertThat(id).isNotNull();
        verify(operacaoSocioRepository, never()).save(any());
    }

    // --- helpers ---

    private ContratoOperacaoCommand pfCommand() {
        return new ContratoOperacaoCommand(1L, new BigDecimal("1000"), Segmento.PF,
                "101A", "0123456789", null);
    }

    private ContratoOperacaoCommand pjCommand() {
        return new ContratoOperacaoCommand(2L, new BigDecimal("10000"), Segmento.PJ,
                "202B", "9876543210", null);
    }

    private ContratoOperacaoCommand agroCommand(BigDecimal areaBeneficiadaHa) {
        return new ContratoOperacaoCommand(3L, new BigDecimal("5000"), Segmento.AGRO,
                "303C", "1234567890", areaBeneficiadaHa);
    }
}
```

---

## Step 3 — Unit test: ConsultarOperacaoCreditoServiceTest

**File:** `src/test/java/br/com/sicredi/creditservice/application/usecase/ConsultarOperacaoCreditoServiceTest.java`

```java
package br.com.sicredi.creditservice.application.usecase;

import br.com.sicredi.creditservice.domain.exception.OperacaoNaoEncontradaException;
import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarOperacaoCreditoServiceTest {

    @Mock OperacaoCreditoRepository operacaoCreditoRepository;

    ConsultarOperacaoCreditoService service;

    @BeforeEach
    void setUp() {
        service = new ConsultarOperacaoCreditoService(operacaoCreditoRepository);
    }

    @Test
    void shouldReturnOperacaoWhenFound() {
        UUID id = UUID.randomUUID();
        OperacaoCredito expected = operacao(id);
        when(operacaoCreditoRepository.findById(id)).thenReturn(Optional.of(expected));

        OperacaoCredito result = service.executar(id);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldThrowWhenOperacaoNotFound() {
        UUID id = UUID.randomUUID();
        when(operacaoCreditoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executar(id))
                .isInstanceOf(OperacaoNaoEncontradaException.class)
                .hasMessageContaining(id.toString());
    }

    private OperacaoCredito operacao(UUID id) {
        return new OperacaoCredito(id, 1L, new BigDecimal("1000"), Segmento.PF,
                "101A", "0123456789", null, LocalDateTime.now());
    }
}
```

---

## Step 4 — Integration test

**File:** `src/test/java/br/com/sicredi/creditservice/OperacaoCreditoIT.java`

Full Spring context. PostgreSQL via Testcontainers. Product service via WireMock.

**Wiring note:** WireMock is started in a static initializer so its port is available
before Spring's `@DynamicPropertySource` runs to inject the base URL.

```java
package br.com.sicredi.creditservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OperacaoCreditoIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("credit_service_test")
            .withUsername("postgres")
            .withPassword("postgres");

    static WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("produtos-credito.base-url", wireMock::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Autowired MockMvc mockMvc;

    // --- POST: happy paths ---

    @Test
    void shouldContractPfOperacao() throws Exception {
        stubEligible();

        mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idAssociado": 1,
                                  "valorOperacao": 1000,
                                  "segmento": "PF",
                                  "codigoProdutoCredito": "101A",
                                  "codigoConta": "0123456789"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOperacaoCredito").isNotEmpty());
    }

    @Test
    void shouldContractPjOperacaoAndPersistSocioRecord() throws Exception {
        stubEligible();

        mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idAssociado": 2,
                                  "valorOperacao": 10000,
                                  "segmento": "PJ",
                                  "codigoProdutoCredito": "202B",
                                  "codigoConta": "9876543210"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOperacaoCredito").isNotEmpty());
    }

    @Test
    void shouldContractAgroOperacao() throws Exception {
        stubEligible();

        mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idAssociado": 3,
                                  "valorOperacao": 5000,
                                  "segmento": "AGRO",
                                  "codigoProdutoCredito": "303C",
                                  "codigoConta": "1234567890",
                                  "areaBeneficiadaHa": 10.5
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOperacaoCredito").isNotEmpty());
    }

    // --- POST: business rejections ---

    @Test
    void shouldReturn422WhenAgroHasNoArea() throws Exception {
        mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idAssociado": 3,
                                  "valorOperacao": 5000,
                                  "segmento": "AGRO",
                                  "codigoProdutoCredito": "303C",
                                  "codigoConta": "1234567890"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldReturn422WhenEligibilityDenied() throws Exception {
        stubNotEligible();

        mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idAssociado": 1,
                                  "valorOperacao": 1000,
                                  "segmento": "PF",
                                  "codigoProdutoCredito": "101A",
                                  "codigoConta": "0123456789"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldReturn503WhenProductServiceFails() throws Exception {
        wireMock.stubFor(get(urlMatching("/produtos-credito/.*"))
                .willReturn(serverError()));

        mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idAssociado": 1,
                                  "valorOperacao": 1000,
                                  "segmento": "PF",
                                  "codigoProdutoCredito": "101A",
                                  "codigoConta": "0123456789"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // --- GET ---

    @Test
    void shouldReturnOperacaoById() throws Exception {
        stubEligible();

        String response = mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idAssociado": 1,
                                  "valorOperacao": 1000,
                                  "segmento": "PF",
                                  "codigoProdutoCredito": "101A",
                                  "codigoConta": "0123456789"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("idOperacaoCredito").asText();

        mockMvc.perform(get("/operacoes-credito/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idOperacaoCredito").value(id))
                .andExpect(jsonPath("$.idAssociado").value(1))
                .andExpect(jsonPath("$.segmento").value("PF"))
                .andExpect(jsonPath("$.codigoProdutoCredito").value("101A"))
                .andExpect(jsonPath("$.codigoConta").value("0123456789"))
                .andExpect(jsonPath("$.dataContratacao").isNotEmpty());
    }

    @Test
    void shouldReturn404WhenOperacaoNotFound() throws Exception {
        mockMvc.perform(get("/operacoes-credito/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // --- WireMock stubs ---

    private void stubEligible() {
        wireMock.stubFor(get(urlMatching("/produtos-credito/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"permiteContratar\": true}")));
    }

    private void stubNotEligible() {
        wireMock.stubFor(get(urlMatching("/produtos-credito/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"permiteContratar\": false}")));
    }
}
```

---

## Test coverage summary

| Test | Class | Scenario |
|---|---|---|
| Unit | `ContratarOperacaoCreditoServiceTest` | AGRO + null area → 422 |
| Unit | `ContratarOperacaoCreditoServiceTest` | AGRO + zero area → 422 |
| Unit | `ContratarOperacaoCreditoServiceTest` | Eligibility denied → 422 |
| Unit | `ContratarOperacaoCreditoServiceTest` | PF happy path → no socio record |
| Unit | `ContratarOperacaoCreditoServiceTest` | PJ happy path → socio record saved |
| Unit | `ContratarOperacaoCreditoServiceTest` | AGRO happy path → no socio record |
| Unit | `ConsultarOperacaoCreditoServiceTest` | ID found → returns operation |
| Unit | `ConsultarOperacaoCreditoServiceTest` | ID not found → 404 exception |
| Integration | `OperacaoCreditoIT` | POST PF → 201 |
| Integration | `OperacaoCreditoIT` | POST PJ → 201 |
| Integration | `OperacaoCreditoIT` | POST AGRO → 201 |
| Integration | `OperacaoCreditoIT` | POST AGRO no area → 422 |
| Integration | `OperacaoCreditoIT` | POST eligibility denied → 422 |
| Integration | `OperacaoCreditoIT` | POST product service down → 503 |
| Integration | `OperacaoCreditoIT` | GET by ID → 200 all fields |
| Integration | `OperacaoCreditoIT` | GET unknown ID → 404 |

---

## Final package layout after Phase 6

```
src/test/java/br/com/sicredi/creditservice
 application
   └── usecase
       ├── ContratarOperacaoCreditoServiceTest.java
       └── ConsultarOperacaoCreditoServiceTest.java
 OperacaoCreditoIT.java

src/test/resources
 application-test.yml
```

---

## Validation checklist

Before considering the service complete, confirm:

- [ ] Unit tests pass with no Spring context (`@ExtendWith(MockitoExtension.class)` only)
- [ ] AGRO null area and AGRO zero area are covered as separate test cases
- [ ] PJ test verifies the `OperacaoSocio` record is persisted with the correct `idOperacaoCredito`
- [ ] Integration tests start a real PostgreSQL container via Testcontainers
- [ ] WireMock is started in a static initializer so its port is available before `@DynamicPropertySource`
- [ ] `application-test.yml` overrides `ddl-auto` to `create-drop`
- [ ] 503 test uses WireMock `serverError()` to simulate product service failure
- [ ] GET test reuses the ID returned by a prior POST (round-trip verification)

---

## Step 5 — Smoke test against the real challenge API

### Purpose

This test calls the **real** `wiremockapi.cloud` product service defined in the challenge — not a local stub.
It proves the full service stack works end-to-end against the actual external contract.

- Uses Testcontainers for PostgreSQL (real DB, same as `OperacaoCreditoIT`)
- Does **not** start a local WireMock — the product service URL is the real challenge URL
- Tagged `@Tag("smoke")` so it can be excluded from routine CI if network is unavailable

> **Note:** This test requires internet access to reach `https://desafio-credito-sicredi.wiremockapi.cloud`.
> Run it manually or in a CI environment with outbound HTTPS allowed.

---

### Step 5.1 — application-smoke.yml

Create `src/test/resources/application-smoke.yml`:

```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:15:///creditdb
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
    username: test
    password: test
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

produtos-credito:
  base-url: https://desafio-credito-sicredi.wiremockapi.cloud
```

The `base-url` points directly to the challenge cloud mock — no override, no local stub.

---

### Step 5.2 — `OperacaoCreditoSmokeIT.java`

**Package:** `br.com.sicredi.creditservice`
**File:** `src/test/java/br/com/sicredi/creditservice/OperacaoCreditoSmokeIT.java`

```java
package br.com.sicredi.creditservice;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("smoke")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("smoke")
class OperacaoCreditoSmokeIT {

    @Autowired
    private MockMvc mockMvc;

    // Product 101A: PF, 500–5000
    @Test
    void postPF_realExternalService_returns201() throws Exception {
        String body = """
                {
                  "idAssociado": 1,
                  "valorOperacao": 1000,
                  "segmento": "PF",
                  "codigoProdutoCredito": "101A",
                  "codigoConta": "1234567890"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOperacaoCredito").isNotEmpty())
                .andReturn();

        // Round-trip: GET the created operation
        String responseBody = result.getResponse().getContentAsString();
        String idOperacaoCredito = com.fasterxml.jackson.databind.json.JsonMapper
                .builder().build()
                .readTree(responseBody)
                .get("idOperacaoCredito")
                .asText();

        mockMvc.perform(get("/api/v1/operacoes-credito/{id}", idOperacaoCredito))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idOperacaoCredito").value(idOperacaoCredito))
                .andExpect(jsonPath("$.segmento").value("PF"))
                .andExpect(jsonPath("$.codigoProdutoCredito").value("101A"))
                .andExpect(jsonPath("$.codigoConta").value("1234567890"));
    }

    // Product 202B: PJ, 5000–50000 — also creates operacao_socio record
    @Test
    void postPJ_realExternalService_returns201() throws Exception {
        String body = """
                {
                  "idAssociado": 2,
                  "valorOperacao": 10000,
                  "segmento": "PJ",
                  "codigoProdutoCredito": "202B",
                  "codigoConta": "9876543210"
                }
                """;

        mockMvc.perform(post("/api/v1/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOperacaoCredito").isNotEmpty());
    }

    // Product 303C: AGRO, 2000–15000 — requires areaBeneficiadaHa
    @Test
    void postAGRO_realExternalService_returns201() throws Exception {
        String body = """
                {
                  "idAssociado": 3,
                  "valorOperacao": 5000,
                  "segmento": "AGRO",
                  "codigoProdutoCredito": "303C",
                  "codigoConta": "1111111111",
                  "areaBeneficiadaHa": 10.5
                }
                """;

        mockMvc.perform(post("/api/v1/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOperacaoCredito").isNotEmpty());
    }

    // Product 101A: PF but value 9999 exceeds max (5000) → real service returns permiteContratar=false
    @Test
    void postPF_valueOutOfRange_realServiceDenies_returns422() throws Exception {
        String body = """
                {
                  "idAssociado": 4,
                  "valorOperacao": 9999,
                  "segmento": "PF",
                  "codigoProdutoCredito": "101A",
                  "codigoConta": "2222222222"
                }
                """;

        mockMvc.perform(post("/api/v1/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    // AGRO without area — rejected before reaching external service
    @Test
    void postAGRO_missingArea_returns422() throws Exception {
        String body = """
                {
                  "idAssociado": 5,
                  "valorOperacao": 5000,
                  "segmento": "AGRO",
                  "codigoProdutoCredito": "303C",
                  "codigoConta": "3333333333"
                }
                """;

        mockMvc.perform(post("/api/v1/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }
}
```

---

### Smoke test coverage matrix

| Test | Segment | Product | Value | External call | Expected |
|---|---|---|---|---|---|
| `postPF_realExternalService_returns201` | PF | 101A | 1,000 | Real API → `true` | 201 + GET round-trip |
| `postPJ_realExternalService_returns201` | PJ | 202B | 10,000 | Real API → `true` | 201 + socio record |
| `postAGRO_realExternalService_returns201` | AGRO | 303C | 5,000 | Real API → `true` | 201 |
| `postPF_valueOutOfRange_realServiceDenies_returns422` | PF | 101A | 9,999 | Real API → `false` | 422 |
| `postAGRO_missingArea_returns422` | AGRO | 303C | 5,000 | None (rejected early) | 422 |

---

### Updated package layout after Step 5

```
src/test/java/br/com/sicredi/creditservice
 application
   └── usecase
       ├── ContratarOperacaoCreditoServiceTest.java
       └── ConsultarOperacaoCreditoServiceTest.java
 OperacaoCreditoIT.java         ← local WireMock
 OperacaoCreditoSmokeIT.java    ← real wiremockapi.cloud

src/test/resources
 application-test.yml           ← profile: test  (local WireMock URL injected)
 application-smoke.yml          ← profile: smoke (real challenge URL)
```

---

### Updated validation checklist (smoke addition)

- [ ] `OperacaoCreditoSmokeIT` is annotated `@Tag("smoke")` and `@ActiveProfiles("smoke")`
- [ ] `application-smoke.yml` sets `base-url` to the real `wiremockapi.cloud` URL — no local stubs
- [ ] PF round-trip test: POST returns `idOperacaoCredito`, GET returns all fields
- [ ] PF out-of-range test relies on the real API returning `permiteContratar: false` for value 9,999 with product 101A (max 5,000)
- [ ] AGRO missing area test passes without any external call — business rule fires first
- [ ] Smoke tests can be skipped in offline CI with Maven: `mvn test -Dgroups='!smoke'`
