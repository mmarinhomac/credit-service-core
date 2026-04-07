package br.com.sicredi.creditservice.domain.port.in;

import br.com.sicredi.creditservice.domain.model.OperacaoCredito;

import java.util.UUID;

public interface ConsultarOperacaoCreditoUseCase {
    OperacaoCredito executar(UUID idOperacaoCredito);
}
