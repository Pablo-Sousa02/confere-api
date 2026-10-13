package com.pablosousa.confere.importacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "erros_importacao")
public class ErroImportacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacao_id", nullable = false)
    private Importacao importacao;

    @Column(name = "numero_linha", nullable = false)
    private Integer numeroLinha;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    protected ErroImportacao() {
    }

    public ErroImportacao(Importacao importacao, Integer numeroLinha, String mensagem) {
        this.importacao = importacao;
        this.numeroLinha = numeroLinha;
        this.mensagem = mensagem;
    }

    public Long getId() {
        return id;
    }

    public Importacao getImportacao() {
        return importacao;
    }

    public Integer getNumeroLinha() {
        return numeroLinha;
    }

    public String getMensagem() {
        return mensagem;
    }
}
