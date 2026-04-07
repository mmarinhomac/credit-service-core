package br.com.sicredi.creditservice.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OperacaoCreditoResponse(
        UUID idOperacaoCredito,
        Long idAssociado,
        BigDecimal valorOperacao,
        String segmento,
        String codigoProdutoCredito,
        String codigoConta,
        BigDecimal areaBeneficiadaHa,
        LocalDateTime dataContratacao
) {}
