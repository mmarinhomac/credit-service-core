# Phase 4 — Infrastructure Adapters

## References

- `copilot/architecture.md` — adapter placement, Resilience4j decisions, ACL boundary
- `copilot/battle-plan.md` — phase scope, persistence and integration adapter list
- `copilot/challenge.md` — external endpoint URL, response shape, table fields
- `copilot/design.md` — component boundaries (infrastructure may depend on Spring Data / Resilience4j, must NOT depend on web/application)
- `copilot/hurdles.md` — non-negotiable rules

---

## Goal

Implement all out-port interfaces defined in Phase 2.
This phase touches three areas: persistence, external integration, and supporting config.

Each adapter is independently testable:
- Persistence adapters → test with an in-memory or containerized database
- Integration adapter → test with a WireMock stub (Phase 6)

Infrastructure classes **may** use Spring and framework annotations.
They must never import from `application.usecase` or `web`.

---

## Package roots

```
br.com.sicredi.creditservice
 infrastructure
   ├── persistence          ← JPA entities, Spring Data repositories, adapters
 integration   └─
       └── produtos         ← HTTP client, Resilience4j adapter
 config                   ← Spring bean wiring
```

---

## Step 0 — pom.xml: add Resilience4j dependencies

Resilience4j is not in the current `pom.xml`. Add both dependencies before writing any adapter code.
The `aop` starter is required for annotation-based circuit breaker and retry to work.

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## Step 1 — application.yml: add external service URL and Resilience4j config

Append to the existing `application.yml`:

```yaml
produtos-credito:
  base-url: https://desafio-credito-sicredi.wiremockapi.cloud

resilience4j:
  circuitbreaker:
    instances:
      produtoElegibilidade:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 2
  retry:
    instances:
      produtoElegibilidade:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
```

---

## Step 2 — Transaction boundary (Phase 3 amendment)

The contracting use case saves two records (`operacao_credito` + `operacao_socio`) that must
be atomic. Adding `@Transactional` to the use case implementation is the accepted pragmatic
compromise — it is a declarative annotation that expresses intent, not business logic.

Update `ContratarOperacaoCreditoService.executar()`:

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Override
public UUID executar(ContratoOperacaoCommand command) { ... }
```

> This is the only Spring import permitted in the application layer and only for this reason.
> `ConsultarOperacaoCreditoService` does not need `@Transactional` — it is a read-only query.

---

## Step 3 — OperacaoCreditoJpaEntity

**File:** `infrastructure/persistence/OperacaoCreditoJpaEntity.java`

JPA representation of the `operacao_credito` table. Separate from the domain record.
Column names follow PostgreSQL snake_case convention.

```java
package br.com.sicredi.creditservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "operacao_credito")
public class OperacaoCreditoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_associado", nullable = false)
    private Long idAssociado;

    @Column(name = "valor_operacao", nullable = false)
    private BigDecimal valorOperacao;

    @Column(name = "segmento", nullable = false)
    private String segmento;

    @Column(name = "codigo_produto_credito", nullable = false)
    private String codigoProdutoCredito;

    @Column(name = "codigo_conta", nullable = false)
    private String codigoConta;

    @Column(name = "area_beneficiada_ha")
    private BigDecimal areaBeneficiadaHa;

    @Column(name = "data_contratacao", nullable = false)
    private LocalDateTime dataContratacao;

    protected OperacaoCreditoJpaEntity() {}

    public OperacaoCreditoJpaEntity(UUID id, Long idAssociado, BigDecimal valorOperacao,
                                    String segmento, String codigoProdutoCredito,
                                    String codigoConta, BigDecimal areaBeneficiadaHa,
                                    LocalDateTime dataContratacao) {
        this.id = id;
        this.idAssociado = idAssociado;
        this.valorOperacao = valorOperacao;
        this.segmento = segmento;
        this.codigoProdutoCredito = codigoProdutoCredito;
        this.codigoConta = codigoConta;
        this.areaBeneficiadaHa = areaBeneficiadaHa;
        this.dataContratacao = dataContratacao;
    }

    public UUID getId() { return id; }
    public Long getIdAssociado() { return idAssociado; }
    public BigDecimal getValorOperacao() { return valorOperacao; }
    public String getSegmento() { return segmento; }
    public String getCodigoProdutoCredito() { return codigoProdutoCredito; }
    public String getCodigoConta() { return codigoConta; }
    public BigDecimal getAreaBeneficiadaHa() { return areaBeneficiadaHa; }
    public LocalDateTime getDataContratacao() { return dataContratacao; }
}
```

**Why `segmento` is stored as `String`:**
Storing the enum name as VARCHAR avoids JPA/DB enum sync issues and keeps the column
readable without needing a DB-level enum type.

---

## Step 4 — OperacaoSocioJpaEntity

**File:** `infrastructure/persistence/OperacaoSocioJpaEntity.java`

JPA representation of the `operacao_socio` table.
`idOperacaoCredito` is the primary key — one PJ operation produces exactly one socio record.

```java
package br.com.sicredi.creditservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "operacao_socio")
public class OperacaoSocioJpaEntity {

    @Id
    @Column(name = "id_operacao_credito", nullable = false, updatable = false)
    private UUID idOperacaoCredito;

    @Column(name = "id_associado", nullable = false)
    private Long idAssociado;

    protected OperacaoSocioJpaEntity() {}

    public OperacaoSocioJpaEntity(UUID idOperacaoCredito, Long idAssociado) {
        this.idOperacaoCredito = idOperacaoCredito;
        this.idAssociado = idAssociado;
    }

    public UUID getIdOperacaoCredito() { return idOperacaoCredito; }
    public Long getIdAssociado() { return idAssociado; }
}
```

---

## Step 5 — Spring Data JPA repository interfaces

These are Spring Data interfaces — not the domain ports. Named with the `Jpa` suffix
to avoid collision with the port interfaces in `domain.port.out`.

**File:** `infrastructure/persistence/OperacaoCreditoJpaRepository.java`

```java
package br.com.sicredi.creditservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OperacaoCreditoJpaRepository extends JpaRepository<OperacaoCreditoJpaEntity, UUID> {}
```

**File:** `infrastructure/persistence/OperacaoSocioJpaRepository.java`

```java
package br.com.sicredi.creditservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OperacaoSocioJpaRepository extends JpaRepository<OperacaoSocioJpaEntity, UUID> {}
```

Both are package-private (`interface`, not `public interface`). Only the adapters
in the same package use them — no other layer may reference them directly.

---

## Step 6 — OperacaoCreditoRepositoryAdapter

**File:** `infrastructure/persistence/OperacaoCreditoRepositoryAdapter.java`

Implements the domain port `OperacaoCreditoRepository`. Translates between JPA entity and domain record.

```java
package br.com.sicredi.creditservice.infrastructure.persistence;

import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class OperacaoCreditoRepositoryAdapter implements OperacaoCreditoRepository {

    private final OperacaoCreditoJpaRepository jpaRepository;

    public OperacaoCreditoRepositoryAdapter(OperacaoCreditoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OperacaoCredito save(OperacaoCredito operacao) {
        OperacaoCreditoJpaEntity entity = toEntity(operacao);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<OperacaoCredito> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private OperacaoCreditoJpaEntity toEntity(OperacaoCredito domain) {
        return new OperacaoCreditoJpaEntity(
                domain.idOperacaoCredito(),
                domain.idAssociado(),
                domain.valorOperacao(),
                domain.segmento().name(),
                domain.codigoProdutoCredito(),
                domain.codigoConta(),
                domain.areaBeneficiadaHa(),
                domain.dataContratacao()
        );
    }

    private OperacaoCredito toDomain(OperacaoCreditoJpaEntity entity) {
        return new OperacaoCredito(
                entity.getId(),
                entity.getIdAssociado(),
                entity.getValorOperacao(),
                Segmento.valueOf(entity.getSegmento()),
                entity.getCodigoProdutoCredito(),
                entity.getCodigoConta(),
                entity.getAreaBeneficiadaHa(),
                entity.getDataContratacao()
        );
    }
}
```

---

## Step 7 — OperacaoSocioRepositoryAdapter

**File:** `infrastructure/persistence/OperacaoSocioRepositoryAdapter.java`

```java
package br.com.sicredi.creditservice.infrastructure.persistence;

import br.com.sicredi.creditservice.domain.model.OperacaoSocio;
import br.com.sicredi.creditservice.domain.port.out.OperacaoSocioRepository;
import org.springframework.stereotype.Component;

@Component
public class OperacaoSocioRepositoryAdapter implements OperacaoSocioRepository {

    private final OperacaoSocioJpaRepository jpaRepository;

    public OperacaoSocioRepositoryAdapter(OperacaoSocioJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OperacaoSocio socio) {
        jpaRepository.save(new OperacaoSocioJpaEntity(
                socio.idOperacaoCredito(),
                socio.idAssociado()
        ));
    }
}
```

---

## Step 8 — ProdutoCreditoResponse

**File:** `infrastructure/integration/produtos/ProdutoCreditoResponse.java`

JSON response record. Jackson deserializes the API response into this type.
It never leaves the integration package.

```java
package br.com.sicredi.creditservice.infrastructure.integration.produtos;

record ProdutoCreditoResponse(boolean permiteContratar) {}
```

Package-private — only the adapter uses it.

---

## Step 9 — ProdutoElegibilidadeAdapter

**File:** `infrastructure/integration/produtos/ProdutoElegibilidadeAdapter.java`

Implements the domain port `ProdutoElegibilidadePort`.
Applies Circuit Breaker and Retry via Resilience4j annotations.
Throws `ProdutoServiceIndisponivelException` (domain exception) on fallback.

```java
package br.com.sicredi.creditservice.infrastructure.integration.produtos;

import br.com.sicredi.creditservice.domain.exception.ProdutoServiceIndisponivelException;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.out.ProdutoElegibilidadePort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProdutoElegibilidadeAdapter implements ProdutoElegibilidadePort {

    private final RestClient restClient;

    public ProdutoElegibilidadeAdapter(RestClient produtoCreditoRestClient) {
        this.restClient = produtoCreditoRestClient;
    }

    @Override
    @CircuitBreaker(name = "produtoElegibilidade", fallbackMethod = "fallback")
    @Retry(name = "produtoElegibilidade")
    public boolean verificar(String codigoProdutoCredito, Segmento segmento, BigDecimal valorOperacao) {
        ProdutoCreditoResponse response = restClient.get()
                .uri("/produtos-credito/{codigo}/permite-contratacao?segmento={segmento}&valorFinanciado={valor}",
                        codigoProdutoCredito, segmento.name(), valorOperacao)
                .retrieve()
                .body(ProdutoCreditoResponse.class);
        return response != null && response.permiteContratar();
    }

    private boolean fallback(String codigoProdutoCredito, Segmento segmento, BigDecimal valorOperacao, Throwable ex) {
        throw new ProdutoServiceIndisponivelException(ex);
    }
}
```

Fix the missing import — add at the top:
```java
import java.math.BigDecimal;
```

**Resilience4j behaviour:**
- `@Retry` runs first — up to 3 attempts with exponential backoff (500ms → 1s → 2s)
- `@CircuitBreaker` wraps the retried call — opens after 50% failure rate over 5 calls
- When open, the fallback fires immediately without calling the service
- Fallback throws `ProdutoServiceIndisponivelException` → web layer maps to 503

---

## Step 10 — RestClientConfig

**File:** `config/RestClientConfig.java`

Registers the `RestClient` bean used by `ProdutoElegibilidadeAdapter`.
The bean name `produtoCreditoRestClient` matches the constructor parameter name in the adapter.

```java
package br.com.sicredi.creditservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient produtoCreditoRestClient(@Value("${produtos-credito.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
```

---

## Final package layout after Phase 4

```
br.com.sicredi.creditservice
 infrastructure
   ├── persistence
   │   ├── OperacaoCreditoJpaEntity.java
   │   ├── OperacaoCreditoJpaRepository.java       (package-private)
   │   ├── OperacaoCreditoRepositoryAdapter.java
   │   ├── OperacaoSocioJpaEntity.java
   │   ├── OperacaoSocioJpaRepository.java         (package-private)
   │   └── OperacaoSocioRepositoryAdapter.java
   └── integration
       └── produtos
           ├── ProdutoCreditoResponse.java          (package-private)
           └── ProdutoElegibilidadeAdapter.java
 config
    └── RestClientConfig.java
```

---

## Validation checklist

Before moving to Phase 5, confirm:

- [ ] `pom.xml` includes `resilience4j-spring-boot3` and `spring-boot-starter-aop`
- [ ] `application.yml` has `produtos-credito.base-url` and `resilience4j` config blocks
- [ ] `ContratarOperacaoCreditoService.executar()` is annotated with `@Transactional`
- [ ] Both JPA entities have a `protected` no-arg constructor for JPA
- [ ] `segmento` is stored as `String` in `OperacaoCreditoJpaEntity` — not a JPA `@Enumerated`
- [ ] Both Spring Data JPA repository interfaces are package-private
- [ ] `ProdutoCreditoResponse` and the Spring Data interfaces are package-private
- [ ] `ProdutoElegibilidadeAdapter.fallback` signature matches `verificar` plus a trailing `Throwable`
- [ ] No adapter imports from `application.usecase` or `web`
- [ ] `OperacaoCreditoJpaRepository` and `OperacaoSocioJpaRepository` are NOT named identically to the domain port interfaces
