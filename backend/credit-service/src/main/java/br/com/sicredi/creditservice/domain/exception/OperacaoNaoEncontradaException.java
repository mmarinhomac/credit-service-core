package br.com.sicredi.creditservice.domain.exception;

import java.util.UUID;

public class OperacaoNaoEncontradaException extends RuntimeException {
    public OperacaoNaoEncontradaException(UUID id) {
        super("Credit operation not found: %s".formatted(id));
    }
}
