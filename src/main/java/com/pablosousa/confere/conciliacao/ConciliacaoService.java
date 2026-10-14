package com.pablosousa.confere.conciliacao;
import com.pablosousa.confere.auth.EmpresaAtual;
import com.pablosousa.confere.importacao.*;
import com.pablosousa.confere.lancamento.LancamentoRepository;
import com.pablosousa.confere.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.*;
@Service
public class ConciliacaoService {
    private final ImportacaoRepository importacoes;
    private final LancamentoRepository lancamentos;
    private final EmpresaAtual atual;
    private final Conciliador conciliador;
    private final ConciliacaoRepository historico;
    private final ResultadoRepository resultados;
    public ConciliacaoService(ImportacaoRepository importacoes, LancamentoRepository lancamentos, EmpresaAtual atual,
                             Conciliador conciliador, ConciliacaoRepository historico, ResultadoRepository resultados) {
        this.importacoes = importacoes; this.lancamentos = lancamentos; this.atual = atual;
        this.conciliador = conciliador; this.historico = historico; this.resultados = resultados;
    }
    public record Execucao(Long id, List<ResultadoConciliacao> resultados) {}
    @Transactional
    public Execucao conciliar(Long importacaoVendasId, Long importacaoRecebimentosId) {
        Long empresaId = atual.id();
        var vendas = importacoes.findByIdAndEmpresa_Id(importacaoVendasId, empresaId).orElseThrow(NaoEncontradoException::new);
        var recebimentos = importacoes.findByIdAndEmpresa_Id(importacaoRecebimentosId, empresaId).orElseThrow(NaoEncontradoException::new);
        if (vendas.getTipo() != TipoImportacao.VENDAS)
            throw new IllegalArgumentException("A primeira importação precisa ser do tipo VENDAS.");
        if (recebimentos.getTipo() != TipoImportacao.RECEBIMENTOS)
            throw new IllegalArgumentException("A segunda importação precisa ser do tipo RECEBIMENTOS.");
        var linhasVendas = lancamentos.findAllByImportacao_IdAndImportacao_Empresa_Id(importacaoVendasId, empresaId);
        var linhasRecebimentos = lancamentos.findAllByImportacao_IdAndImportacao_Empresa_Id(importacaoRecebimentosId, empresaId);
        var calculados = conciliador.calcular(linhasVendas, linhasRecebimentos);
        lancamentos.saveAll(linhasVendas);
        lancamentos.saveAll(linhasRecebimentos);
        var execucao = historico.save(new Conciliacao(empresaId, importacaoVendasId, importacaoRecebimentosId, atual.identidade().usuarioId()));
        resultados.saveAll(calculados.stream().map(r -> new ResultadoPersistido(execucao, r)).toList());
        return new Execucao(execucao.getId(), calculados);
    }
    @Transactional(readOnly=true)
    public Pagina<ConciliacaoResumo> listar(int page, int size, Long importacaoId, Instant desde, Instant ate) {
        if (desde != null && ate != null && desde.isAfter(ate)) throw new IllegalArgumentException("Intervalo de datas inválido.");
        Long empresaId = atual.id();
        Specification<Conciliacao> filtro = (root, query, cb) -> {
            var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
            p.add(cb.equal(root.get("empresaId"), empresaId));
            if (importacaoId != null) p.add(cb.or(cb.equal(root.get("importacaoVendasId"), importacaoId), cb.equal(root.get("importacaoRecebimentosId"), importacaoId)));
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("criadaEm"), desde));
            if (ate != null) p.add(cb.lessThanOrEqualTo(root.get("criadaEm"), ate));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return Pagina.de(historico.findAll(filtro, Pagina.pedido(page, size)).map(ConciliacaoResumo::de));
    }
    @Transactional(readOnly=true)
    public ConciliacaoResumo obter(Long id) { return ConciliacaoResumo.de(buscar(id)); }
    @Transactional(readOnly=true)
    public Pagina<ResultadoConciliacao> resultados(Long id, int page, int size, StatusConciliacao status, String referencia) {
        buscar(id);
        Long empresaId = atual.id();
        Specification<ResultadoPersistido> filtro = (root, query, cb) -> {
            var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
            p.add(cb.equal(root.get("conciliacao").get("id"), id));
            p.add(cb.equal(root.get("conciliacao").get("empresaId"), empresaId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (referencia != null) p.add(cb.equal(root.get("referencia"), referencia.strip().toUpperCase(Locale.ROOT)));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return Pagina.de(resultados.findAll(filtro, Pagina.pedido(page, size)).map(ResultadoPersistido::dto));
    }
    public record Dashboard(Long conciliacaoId, long conciliados, long divergentes, long vendasSemRecebimento,
                            long recebimentosSemVenda, long referenciasDuplicadas, long referenciasAusentes,
                            String escopo, Instant desde, Instant ate, long execucoes, boolean importacoesSobrepostas) {
        public Dashboard(Long id, long conciliados, long divergentes, long vendas, long recebimentos, long duplicadas, long ausentes) {
            this(id, conciliados, divergentes, vendas, recebimentos, duplicadas, ausentes, "EXECUCAO", null, null, id == null ? 0 : 1, false);
        }
    }
    @Transactional(readOnly=true)
    public Dashboard dashboardPeriodo(Instant desde, Instant ate) {
        if (desde != null && ate != null && desde.isAfter(ate)) throw new IllegalArgumentException("Intervalo de datas inválido.");
        var totais = historico.totaisPeriodo(atual.id(), desde, ate);
        return new Dashboard(null, totais.getConciliados(), totais.getDivergentes(), totais.getVendasSemRecebimento(),
                totais.getRecebimentosSemVenda(), totais.getDuplicadas(), totais.getAusentes(),
                "ULTIMA_EXECUCAO_POR_PAR_NO_PERIODO", desde, ate, totais.getExecucoes(), totais.getSobrepostas());
    }
    @Transactional(readOnly=true)
    public Dashboard dashboard(Long id) {
        var c = id == null ? historico.findFirstByEmpresaIdOrderByIdDesc(atual.id()).orElse(null) : buscar(id);
        if (c == null) return new Dashboard(null, 0, 0, 0, 0, 0, 0);
        return new Dashboard(c.getId(), contar(c, StatusConciliacao.CONCILIADO), contar(c, StatusConciliacao.DIVERGENTE),
                contar(c, StatusConciliacao.VENDA_SEM_RECEBIMENTO), contar(c, StatusConciliacao.RECEBIMENTO_SEM_VENDA),
                contar(c, StatusConciliacao.REFERENCIA_DUPLICADA), contar(c, StatusConciliacao.REFERENCIA_AUSENTE));
    }
    private long contar(Conciliacao c, StatusConciliacao status) {
        return resultados.countByConciliacao_IdAndConciliacao_EmpresaIdAndStatus(c.getId(), atual.id(), status);
    }
    private Conciliacao buscar(Long id) { return historico.findByIdAndEmpresaId(id, atual.id()).orElseThrow(NaoEncontradoException::new); }
}
