package com.pablosousa.confere.conciliacao;
import com.pablosousa.confere.lancamento.Lancamento;
import com.pablosousa.confere.lancamento.StatusLancamento;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class Conciliador {
    private static final String REFERENCIA_AUSENTE_PREFIXO = "\u0000";
    public List<ResultadoConciliacao> calcular(List<Lancamento> vendas, List<Lancamento> recebimentos) {
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
                    || possuiPossivelDuplicidade(recebimentosDaReferencia)) {
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

    private record IdentificadorRecebimento(java.time.LocalDate data, BigDecimal valor, String descricao) {}

    private boolean possuiPossivelDuplicidade(List<Lancamento> recebimentos) {
        Set<IdentificadorRecebimento> vistos = new HashSet<>();
        for (Lancamento r : recebimentos) {
            String descricao = r.getDescricao() == null ? "" : r.getDescricao().strip().toUpperCase(Locale.ROOT);
            var identificador = new IdentificadorRecebimento(r.getDataLancamento(), r.getValor().stripTrailingZeros(), descricao);
            if (!vistos.add(identificador)) return true;
        }
        return false;
    }
}
