package br.com.sicredi.creditservice.domain.exception;

public class ElegibilidadeNegadaException extends RuntimeException {
    public ElegibilidadeNegadaException(String codigoProduto, String segmento) {
        super("Product %s does not allow contracting for segment %s.".formatted(codigoProduto, segmento));
    }
}
