package br.com.sicredi.creditservice.application.usecase;

import br.com.sicredi.creditservice.domain.exception.OperacaoNaoEncontradaException;
import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.port.in.ConsultarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;

import java.util.UUID;

public class ConsultarOperacaoCreditoService implements ConsultarOperacaoCreditoUseCase {

    private final OperacaoCreditoRepository operacaoCreditoRepository;

    public ConsultarOperacaoCreditoService(OperacaoCreditoRepository operacaoCreditoRepository) {
        this.operacaoCreditoRepository = operacaoCreditoRepository;
    }

    @Override
    public OperacaoCredito executar(UUID idOperacaoCredito) {
        return operacaoCreditoRepository.findById(idOperacaoCredito)
                .orElseThrow(() -> new OperacaoNaoEncontradaException(idOperacaoCredito));
    }
}
