package com.pablosousa.confere.conciliacao;
import java.time.Instant;
public record ConciliacaoResumo(Long id, Long importacaoVendasId, Long importacaoRecebimentosId, Instant criadaEm) {
    public static ConciliacaoResumo de(Conciliacao c) {
        return new ConciliacaoResumo(c.getId(), c.getImportacaoVendasId(), c.getImportacaoRecebimentosId(), c.getCriadaEm());
    }
}
