package br.com.sicredi.creditservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "operacao_socio")
public class OperacaoSocioJpaEntity {

    @Id
    @Column(name = "id_operacao_credito", nullable = false, updatable = false)
    private UUID idOperacaoCredito;

    @Column(name = "id_associado", nullable = false)
    private Long idAssociado;

    protected OperacaoSocioJpaEntity() {}

    public OperacaoSocioJpaEntity(UUID idOperacaoCredito, Long idAssociado) {
        this.idOperacaoCredito = idOperacaoCredito;
        this.idAssociado = idAssociado;
    }

    public UUID getIdOperacaoCredito() { return idOperacaoCredito; }
    public Long getIdAssociado() { return idAssociado; }
}
