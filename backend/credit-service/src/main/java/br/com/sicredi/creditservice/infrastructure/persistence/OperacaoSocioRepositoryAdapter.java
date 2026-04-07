package br.com.sicredi.creditservice.infrastructure.persistence;

import br.com.sicredi.creditservice.domain.model.OperacaoSocio;
import br.com.sicredi.creditservice.domain.port.out.OperacaoSocioRepository;
import org.springframework.stereotype.Component;

@Component
public class OperacaoSocioRepositoryAdapter implements OperacaoSocioRepository {

    private final OperacaoSocioJpaRepository jpaRepository;

    public OperacaoSocioRepositoryAdapter(OperacaoSocioJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OperacaoSocio socio) {
        jpaRepository.save(new OperacaoSocioJpaEntity(
                socio.idOperacaoCredito(),
                socio.idAssociado()
        ));
    }
}
