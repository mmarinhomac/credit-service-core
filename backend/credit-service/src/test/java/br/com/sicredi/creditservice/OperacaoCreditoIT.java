package br.com.sicredi.creditservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
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

import java.util.UUID;

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
        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/produtos-credito/.*"))
                .willReturn(WireMock.serverError()));

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
        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/produtos-credito/.*"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"permiteContratar\": true}")));
    }

    private void stubNotEligible() {
        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/produtos-credito/.*"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"permiteContratar\": false}")));
    }
}
