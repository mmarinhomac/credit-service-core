package br.com.sicredi.creditservice.infrastructure.persistence;

import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class OperacaoCreditoRepositoryAdapter implements OperacaoCreditoRepository {

    private final OperacaoCreditoJpaRepository jpaRepository;

    public OperacaoCreditoRepositoryAdapter(OperacaoCreditoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OperacaoCredito save(OperacaoCredito operacao) {
        return toDomain(jpaRepository.save(toEntity(operacao)));
    }

    @Override
    public Optional<OperacaoCredito> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private OperacaoCreditoJpaEntity toEntity(OperacaoCredito domain) {
        return new OperacaoCreditoJpaEntity(
                domain.idOperacaoCredito(),
                domain.idAssociado(),
                domain.valorOperacao(),
                domain.segmento().name(),
                domain.codigoProdutoCredito(),
                domain.codigoConta(),
                domain.areaBeneficiadaHa(),
                domain.dataContratacao()
        );
    }

    private OperacaoCredito toDomain(OperacaoCreditoJpaEntity entity) {
        return new OperacaoCredito(
                entity.getId(),
                entity.getIdAssociado(),
                entity.getValorOperacao(),
                Segmento.valueOf(entity.getSegmento()),
                entity.getCodigoProdutoCredito(),
                entity.getCodigoConta(),
                entity.getAreaBeneficiadaHa(),
                entity.getDataContratacao()
        );
    }
}
