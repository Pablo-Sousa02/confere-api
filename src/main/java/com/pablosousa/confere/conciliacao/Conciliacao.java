package com.pablosousa.confere.conciliacao;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="conciliacoes")
public class Conciliacao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="empresa_id", nullable=false) private Long empresaId;
    @Column(name="importacao_vendas_id", nullable=false) private Long importacaoVendasId;
    @Column(name="importacao_recebimentos_id", nullable=false) private Long importacaoRecebimentosId;
    @Column(name="usuario_id", nullable=false) private Long usuarioId;
    @Column(name="criada_em", nullable=false) private Instant criadaEm;
    protected Conciliacao() {}
    public Conciliacao(Long empresaId, Long vendas, Long recebimentos, Long usuarioId) {
        this.empresaId = empresaId; this.importacaoVendasId = vendas; this.importacaoRecebimentosId = recebimentos;
        this.usuarioId = usuarioId; this.criadaEm = Instant.now();
    }
    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getImportacaoVendasId() { return importacaoVendasId; }
    public Long getImportacaoRecebimentosId() { return importacaoRecebimentosId; }
    public Instant getCriadaEm() { return criadaEm; }
}
