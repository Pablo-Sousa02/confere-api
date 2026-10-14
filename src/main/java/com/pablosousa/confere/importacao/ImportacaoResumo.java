package com.pablosousa.confere.importacao;

public record ImportacaoResumo(
        Long id,
        String arquivo,
        TipoImportacao tipo,
        StatusImportacao status,
        long linhasImportadas,
        long linhasComErro
) {
}