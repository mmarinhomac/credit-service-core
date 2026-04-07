package br.com.sicredi.creditservice.domain.port.in;

import br.com.sicredi.creditservice.domain.model.Segmento;

import java.math.BigDecimal;

public record ContratoOperacaoCommand(
        Long idAssociado,
        BigDecimal valorOperacao,
        Segmento segmento,
        String codigoProdutoCredito,
        String codigoConta,
        BigDecimal areaBeneficiadaHa
) {}
