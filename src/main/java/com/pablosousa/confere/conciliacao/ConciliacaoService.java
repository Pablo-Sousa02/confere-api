package com.pablosousa.confere.conciliacao;

import com.pablosousa.confere.importacao.Importacao;
import com.pablosousa.confere.importacao.ImportacaoRepository;
import com.pablosousa.confere.importacao.TipoImportacao;
import com.pablosousa.confere.lancamento.Lancamento;
import com.pablosousa.confere.lancamento.LancamentoRepository;
import com.pablosousa.confere.lancamento.StatusLancamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ConciliacaoService {

    private static final String REFERENCIA_AUSENTE_PREFIXO = "\u0000";

    private final ImportacaoRepository importacaoRepository;
    private final LancamentoRepository lancamentoRepository;

    public ConciliacaoService(
            ImportacaoRepository importacaoRepository,
            LancamentoRepository lancamentoRepository
    ) {
        this.importacaoRepository = importacaoRepository;
        this.lancamentoRepository = lancamentoRepository;
    }

    @Transactional
    public List<ResultadoConciliacao> conciliar(
            Long importacaoVendasId,
            Long importacaoRecebimentosId
    ) {
        Importacao importacaoVendas = importacaoRepository.findById(importacaoVendasId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Importação de vendas não encontrada."
                ));

        Importacao importacaoRecebimentos =
                importacaoRepository.findById(importacaoRecebimentosId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Importação de recebimentos não encontrada."
                        ));

        if (importacaoVendas.getTipo() != TipoImportacao.VENDAS) {
            throw new IllegalArgumentException(
                    "A primeira importação precisa ser do tipo VENDAS."
            );
        }

        if (importacaoRecebimentos.getTipo() != TipoImportacao.RECEBIMENTOS) {
            throw new IllegalArgumentException(
                    "A segunda importação precisa ser do tipo RECEBIMENTOS."
            );
        }

        List<Lancamento> vendas =
                lancamentoRepository.findAllByImportacao_Id(importacaoVendasId);

        List<Lancamento> recebimentos =
                lancamentoRepository.findAllByImportacao_Id(importacaoRecebimentosId);

        Map<String, List<Lancamento>> vendasPorReferencia = agruparPorReferencia(vendas);
        Map<String, List<Lancamento>> recebimentosPorReferencia =
                agruparPorReferencia(recebimentos);

        Set<String> referencias = new TreeSet<>();
        referencias.addAll(vendasPorReferencia.keySet());
        referencias.addAll(recebimentosPorReferencia.keySet());

        List<ResultadoConciliacao> resultados = new ArrayList<>();

        for (String referencia : referencias) {
            List<Lancamento> vendasDaReferencia =
                    vendasPorReferencia.getOrDefault(referencia, List.of());

            List<Lancamento> recebimentosDaReferencia =
                    recebimentosPorReferencia.getOrDefault(referencia, List.of());

            BigDecimal valorVenda = somarValores(vendasDaReferencia);
            BigDecimal valorRecebido = somarValores(recebimentosDaReferencia);
            BigDecimal diferenca = null;

            StatusConciliacao status;

            if (referencia.startsWith(REFERENCIA_AUSENTE_PREFIXO)) {
                status = StatusConciliacao.REFERENCIA_AUSENTE;
            } else if (vendasDaReferencia.size() > 1
                    || recebimentosDaReferencia.size() > 1) {
                status = StatusConciliacao.REFERENCIA_DUPLICADA;
            } else if (vendasDaReferencia.isEmpty()) {
                status = StatusConciliacao.RECEBIMENTO_SEM_VENDA;
            } else if (recebimentosDaReferencia.isEmpty()) {
                status = StatusConciliacao.VENDA_SEM_RECEBIMENTO;
            } else {
                diferenca = valorRecebido.subtract(valorVenda);

                status = diferenca.compareTo(BigDecimal.ZERO) == 0
                        ? StatusConciliacao.CONCILIADO
                        : StatusConciliacao.DIVERGENTE;
            }

            StatusLancamento statusLancamento =
                    status == StatusConciliacao.CONCILIADO
                            ? StatusLancamento.CONCILIADO
                            : StatusLancamento.DIVERGENTE;

            vendasDaReferencia.forEach(l -> l.setStatus(statusLancamento));
            recebimentosDaReferencia.forEach(l -> l.setStatus(statusLancamento));

            String referenciaExibida =
                    referencia.startsWith(REFERENCIA_AUSENTE_PREFIXO)
                            ? null
                            : referencia;

            resultados.add(new ResultadoConciliacao(
                    referenciaExibida,
                    valorVenda,
                    valorRecebido,
                    diferenca,
                    status
            ));
        }

        lancamentoRepository.saveAll(vendas);
        lancamentoRepository.saveAll(recebimentos);

        return resultados;
    }

    private Map<String, List<Lancamento>> agruparPorReferencia(
            List<Lancamento> lancamentos
    ) {
        Map<String, List<Lancamento>> grupos = new HashMap<>();

        for (Lancamento lancamento : lancamentos) {
            String referencia = lancamento.getReferenciaExterna();

            String chave = referencia == null || referencia.isBlank()
                    ? REFERENCIA_AUSENTE_PREFIXO + lancamento.getId()
                    : referencia.trim().toUpperCase(Locale.ROOT);

            grupos.computeIfAbsent(chave, ignorado -> new ArrayList<>())
                    .add(lancamento);
        }

        return grupos;
    }

    private BigDecimal somarValores(List<Lancamento> lancamentos) {
        if (lancamentos.isEmpty()) {
            return null;
        }

        return lancamentos.stream()
                .map(Lancamento::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
