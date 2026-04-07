package br.com.sicredi.creditservice.domain.port.out;

import br.com.sicredi.creditservice.domain.model.OperacaoCredito;

import java.util.Optional;
import java.util.UUID;

public interface OperacaoCreditoRepository {
    OperacaoCredito save(OperacaoCredito operacao);
    Optional<OperacaoCredito> findById(UUID idOperacaoCredito);
}
