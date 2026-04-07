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

/**
 * Smoke tests — hit the real challenge product service at wiremockapi.cloud.
 * Requires internet access. Excluded from offline CI with: mvn test -Dgroups='!smoke'
 */
@Tag("smoke")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("smoke")
class OperacaoCreditoSmokeIT {

    @Autowired
    private MockMvc mockMvc;

    // Product 101A: PF, 500–5000 → eligible for value 1000
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

        MvcResult result = mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOperacaoCredito").isNotEmpty())
                .andReturn();

        // Round-trip: GET the created operation
        String responseBody = result.getResponse().getContentAsString();
        String idOperacaoCredito = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(responseBody)
                .get("idOperacaoCredito")
                .asText();

        mockMvc.perform(get("/operacoes-credito/{id}", idOperacaoCredito))
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

        mockMvc.perform(post("/operacoes-credito")
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

        mockMvc.perform(post("/operacoes-credito")
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

        mockMvc.perform(post("/operacoes-credito")
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

        mockMvc.perform(post("/operacoes-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }
}
