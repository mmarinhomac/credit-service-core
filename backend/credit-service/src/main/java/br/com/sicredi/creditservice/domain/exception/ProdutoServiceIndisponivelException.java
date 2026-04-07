package br.com.sicredi.creditservice.domain.exception;

public class ProdutoServiceIndisponivelException extends RuntimeException {
    public ProdutoServiceIndisponivelException(Throwable cause) {
        super("Product eligibility service is unavailable.", cause);
    }
}
