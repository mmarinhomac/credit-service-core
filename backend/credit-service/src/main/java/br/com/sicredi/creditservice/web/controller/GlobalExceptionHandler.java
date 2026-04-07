package br.com.sicredi.creditservice.web.controller;

import br.com.sicredi.creditservice.domain.exception.AgroSemAreaException;
import br.com.sicredi.creditservice.domain.exception.ElegibilidadeNegadaException;
import br.com.sicredi.creditservice.domain.exception.OperacaoNaoEncontradaException;
import br.com.sicredi.creditservice.domain.exception.ProdutoServiceIndisponivelException;
import br.com.sicredi.creditservice.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AgroSemAreaException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleAgroSemArea(AgroSemAreaException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(ElegibilidadeNegadaException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleElegibilidadeNegada(ElegibilidadeNegadaException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(OperacaoNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOperacaoNaoEncontrada(OperacaoNaoEncontradaException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(ProdutoServiceIndisponivelException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleProdutoServiceIndisponivel(ProdutoServiceIndisponivelException ex) {
        return new ErrorResponse(ex.getMessage());
    }
}
