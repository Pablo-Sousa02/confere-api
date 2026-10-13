package com.pablosousa.confere;

import com.pablosousa.confere.importacao.ErroImportacao;
import com.pablosousa.confere.importacao.Importacao;
import com.pablosousa.confere.importacao.StatusImportacao;
import com.pablosousa.confere.importacao.TipoImportacao;
import com.pablosousa.confere.lancamento.Lancamento;
import com.pablosousa.confere.lancamento.StatusLancamento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntidadesTests {

    @Test
    void preservaDadosDoErroDeImportacao() {
        Importacao importacao = new Importacao("vendas.csv", TipoImportacao.VENDAS);
        ErroImportacao erro = new ErroImportacao(importacao, 3, "Valor invalido");

        assertEquals(StatusImportacao.PROCESSANDO, importacao.getStatus());
        assertSame(importacao, erro.getImportacao());
        assertEquals(3, erro.getNumeroLinha());
        assertEquals("Valor invalido", erro.getMensagem());
    }

    @Test
    void novoLancamentoComecaPendenteEPreservaDados() {
        Importacao importacao = new Importacao("vendas.csv", TipoImportacao.VENDAS);
        LocalDate data = LocalDate.of(2026, 10, 12);
        BigDecimal valor = new BigDecimal("25.50");
        Lancamento lancamento = new Lancamento(null, importacao, 2, "VENDA-1", data, "Venda", valor);

        assertSame(importacao, lancamento.getImportacao());
        assertEquals(2, lancamento.getNumeroLinha());
        assertEquals("VENDA-1", lancamento.getReferenciaExterna());
        assertEquals(data, lancamento.getDataLancamento());
        assertEquals("Venda", lancamento.getDescricao());
        assertEquals(valor, lancamento.getValor());
        assertEquals(StatusLancamento.PENDENTE, lancamento.getStatus());
    }
}
