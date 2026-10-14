package com.pablosousa.confere.importacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "importacoes")
public class Importacao {

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "empresa_id", nullable = false)
    private com.pablosousa.confere.empresa.Empresa empresa;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoImportacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusImportacao status;

    protected Importacao() {
    }

    public Importacao(String nomeArquivo, TipoImportacao tipo) {
        this.nomeArquivo = nomeArquivo;
        this.tipo = tipo;
        this.status = StatusImportacao.PROCESSANDO;
    }

    public Importacao(String nomeArquivo, TipoImportacao tipo, com.pablosousa.confere.empresa.Empresa empresa) {
        this(nomeArquivo, tipo);
        this.empresa = empresa;
    }

    public com.pablosousa.confere.empresa.Empresa getEmpresa() { return empresa; }

    public Long getId() {
        return id;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public TipoImportacao getTipo() {
        return tipo;
    }

    public StatusImportacao getStatus() {
        return status;
    }

    public void setStatus(StatusImportacao status) {
        this.status = status;
    }
}
