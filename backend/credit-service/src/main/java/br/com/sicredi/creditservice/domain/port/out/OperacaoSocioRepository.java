package br.com.sicredi.creditservice.domain.port.out;

import br.com.sicredi.creditservice.domain.model.OperacaoSocio;

public interface OperacaoSocioRepository {
    void save(OperacaoSocio socio);
}
