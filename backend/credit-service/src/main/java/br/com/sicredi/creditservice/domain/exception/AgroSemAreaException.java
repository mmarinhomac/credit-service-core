package br.com.sicredi.creditservice.domain.exception;

public class AgroSemAreaException extends RuntimeException {
    public AgroSemAreaException() {
        super("Operations with segmento AGRO require areaBeneficiadaHa greater than zero.");
    }
}
