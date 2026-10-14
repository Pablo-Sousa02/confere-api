package com.pablosousa.confere.conciliacao;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface ConciliacaoRepository extends JpaRepository<Conciliacao, Long>, JpaSpecificationExecutor<Conciliacao> {
    interface TotaisPeriodo {
        long getExecucoes();
        boolean getSobrepostas();
        long getConciliados();
        long getDivergentes();
        long getVendasSemRecebimento();
        long getRecebimentosSemVenda();
        long getDuplicadas();
        long getAusentes();
    }
    @Query(value="""
            WITH candidatas AS (
                SELECT c.*, row_number() OVER (
                    PARTITION BY importacao_vendas_id, importacao_recebimentos_id ORDER BY id DESC) AS ordem
                FROM conciliacoes c WHERE empresa_id=:empresaId
                AND criada_em >= coalesce(cast(:desde AS timestamptz), cast('-infinity' AS timestamptz))
                AND criada_em <= coalesce(cast(:ate AS timestamptz), cast('infinity' AS timestamptz))
            ), escolhidas AS (SELECT * FROM candidatas WHERE ordem=1)
            SELECT (SELECT count(*) FROM escolhidas) AS execucoes,
              (SELECT count(DISTINCT importacao_vendas_id)<count(*) OR count(DISTINCT importacao_recebimentos_id)<count(*) FROM escolhidas) AS sobrepostas,
              count(*) FILTER (WHERE r.status='CONCILIADO') AS conciliados,
              count(*) FILTER (WHERE r.status='DIVERGENTE') AS divergentes,
              count(*) FILTER (WHERE r.status='VENDA_SEM_RECEBIMENTO') AS "vendasSemRecebimento",
              count(*) FILTER (WHERE r.status='RECEBIMENTO_SEM_VENDA') AS "recebimentosSemVenda",
              count(*) FILTER (WHERE r.status='REFERENCIA_DUPLICADA') AS duplicadas,
              count(*) FILTER (WHERE r.status='REFERENCIA_AUSENTE') AS ausentes
            FROM resultados_conciliacao r JOIN escolhidas c ON c.id=r.conciliacao_id
            """, nativeQuery=true)
    TotaisPeriodo totaisPeriodo(Long empresaId, java.time.Instant desde, java.time.Instant ate);
    Optional<Conciliacao> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<Conciliacao> findFirstByEmpresaIdOrderByIdDesc(Long empresaId);
}
