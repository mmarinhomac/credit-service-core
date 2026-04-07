package br.com.sicredi.creditservice.web.dto;

import java.math.BigDecimal;

public record ContratoOperacaoRequest(
        Long idAssociado,
        BigDecimal valorOperacao,
        String segmento,
        String codigoProdutoCredito,
        String codigoConta,
        BigDecimal areaBeneficiadaHa
) {}
