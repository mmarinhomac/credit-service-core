package br.com.sicredi.creditservice.domain.model;

import java.util.UUID;

public record OperacaoSocio(
        UUID idOperacaoCredito,
        Long idAssociado
) {}
