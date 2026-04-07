package br.com.sicredi.creditservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "operacao_credito")
public class OperacaoCreditoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_associado", nullable = false)
    private Long idAssociado;

    @Column(name = "valor_operacao", nullable = false)
    private BigDecimal valorOperacao;

    @Column(name = "segmento", nullable = false)
    private String segmento;

    @Column(name = "codigo_produto_credito", nullable = false)
    private String codigoProdutoCredito;

    @Column(name = "codigo_conta", nullable = false)
    private String codigoConta;

    @Column(name = "area_beneficiada_ha")
    private BigDecimal areaBeneficiadaHa;

    @Column(name = "data_contratacao", nullable = false)
    private LocalDateTime dataContratacao;

    protected OperacaoCreditoJpaEntity() {}

    public OperacaoCreditoJpaEntity(UUID id, Long idAssociado, BigDecimal valorOperacao,
                                    String segmento, String codigoProdutoCredito,
                                    String codigoConta, BigDecimal areaBeneficiadaHa,
                                    LocalDateTime dataContratacao) {
        this.id = id;
        this.idAssociado = idAssociado;
        this.valorOperacao = valorOperacao;
        this.segmento = segmento;
        this.codigoProdutoCredito = codigoProdutoCredito;
        this.codigoConta = codigoConta;
        this.areaBeneficiadaHa = areaBeneficiadaHa;
        this.dataContratacao = dataContratacao;
    }

    public UUID getId() { return id; }
    public Long getIdAssociado() { return idAssociado; }
    public BigDecimal getValorOperacao() { return valorOperacao; }
    public String getSegmento() { return segmento; }
    public String getCodigoProdutoCredito() { return codigoProdutoCredito; }
    public String getCodigoConta() { return codigoConta; }
    public BigDecimal getAreaBeneficiadaHa() { return areaBeneficiadaHa; }
    public LocalDateTime getDataContratacao() { return dataContratacao; }
}
