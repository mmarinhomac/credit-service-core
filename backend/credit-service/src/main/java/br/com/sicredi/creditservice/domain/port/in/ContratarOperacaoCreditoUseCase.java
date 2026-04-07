package br.com.sicredi.creditservice.domain.port.in;

import java.util.UUID;

public interface ContratarOperacaoCreditoUseCase {
    UUID executar(ContratoOperacaoCommand command);
}
