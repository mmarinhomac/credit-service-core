package br.com.sicredi.creditservice.infrastructure.integration.produtos;

import br.com.sicredi.creditservice.domain.exception.ProdutoServiceIndisponivelException;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.out.ProdutoElegibilidadePort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

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
