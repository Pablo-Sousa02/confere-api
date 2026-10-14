package com.pablosousa.confere.conciliacao;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="resultados_conciliacao")
public class ResultadoPersistido {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="conciliacao_id", nullable=false)
    private Conciliacao conciliacao;
    @Column(length=100) private String referencia;
    @Column(name="valor_venda", precision=25, scale=2) private BigDecimal valorVenda;
    @Column(name="valor_recebido", precision=25, scale=2) private BigDecimal valorRecebido;
    @Column(precision=25, scale=2) private BigDecimal diferenca;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private StatusConciliacao status;
    protected ResultadoPersistido() {}
    public ResultadoPersistido(Conciliacao c, ResultadoConciliacao r) {
        conciliacao = c; referencia = r.referencia(); valorVenda = r.valorVenda(); valorRecebido = r.valorRecebido();
        diferenca = r.diferenca(); status = r.status();
    }
    public ResultadoConciliacao dto() { return new ResultadoConciliacao(referencia, valorVenda, valorRecebido, diferenca, status); }
}
