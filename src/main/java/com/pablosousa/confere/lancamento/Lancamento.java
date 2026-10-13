package com.pablosousa.confere.lancamento;

import com.pablosousa.confere.importacao.Importacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "lancamentos")
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacao_id", nullable = false)
    private Importacao importacao;

    @Column(name = "numero_linha", nullable = false)
    private Integer numeroLinha;

    @Column(name = "identificador_externo", length = 100)
    private String referenciaExterna;

    @Column(name = "data_movimento", nullable = false)
    private LocalDate dataLancamento;

    private String descricao;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_conciliacao", nullable = false, length = 20)
    private StatusLancamento status;

    // O JPA utiliza este construtor por reflexao.
    @SuppressWarnings("unused")
    protected Lancamento() {
    }

    public Lancamento(
            Long id, Importacao importacao,
            Integer numeroLinha,
            String referenciaExterna,
            LocalDate dataLancamento,
            String descricao,
            BigDecimal valor
    ) {
        this.id = id;
        this.importacao = importacao;
        this.numeroLinha = numeroLinha;
        this.referenciaExterna = referenciaExterna;
        this.dataLancamento = dataLancamento;
        this.descricao = descricao;
        this.valor = valor;
        this.status = StatusLancamento.PENDENTE;
    }

    // Acesso publico ao identificador, ainda sem consumidores no projeto.
    @SuppressWarnings("unused")
    public Long getId() {
        return id;
    }

    public Importacao getImportacao() {
        return importacao;
    }

    public Integer getNumeroLinha() {
        return numeroLinha;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public LocalDate getDataLancamento() {
        return dataLancamento;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public StatusLancamento getStatus() {
        return status;
    }

    public void setStatus(StatusLancamento status) {
        this.status = status;
    }
}
