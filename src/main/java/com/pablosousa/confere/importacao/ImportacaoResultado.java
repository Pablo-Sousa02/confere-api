package com.pablosousa.confere.importacao;

public record ImportacaoResultado(
        Long id,
        String arquivo,
        TipoImportacao tipo,
        StatusImportacao status,
        int linhasImportadas,
        int linhasComErro
) {
}