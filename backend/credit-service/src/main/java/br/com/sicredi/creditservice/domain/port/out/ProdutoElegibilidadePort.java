package br.com.sicredi.creditservice.domain.port.out;

import br.com.sicredi.creditservice.domain.model.Segmento;

import java.math.BigDecimal;

public interface ProdutoElegibilidadePort {
    boolean verificar(String codigoProdutoCredito, Segmento segmento, BigDecimal valorOperacao);
}
