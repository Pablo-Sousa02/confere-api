package com.pablosousa.confere.conciliacao;

import com.pablosousa.confere.importacao.Importacao;
import com.pablosousa.confere.importacao.ImportacaoRepository;
import com.pablosousa.confere.importacao.TipoImportacao;
import com.pablosousa.confere.lancamento.Lancamento;
import com.pablosousa.confere.lancamento.LancamentoRepository;
import com.pablosousa.confere.lancamento.StatusLancamento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConciliacaoServiceTests {
    private final ImportacaoRepository importacoes = mock(ImportacaoRepository.class);
    private final LancamentoRepository lancamentos = mock(LancamentoRepository.class);
    private final ConciliacaoService service = new ConciliacaoService(importacoes, lancamentos);

    @Test
    void classificaReferenciasCalculaDiferencaEAtualizaLancamentos() {
        when(importacoes.findById(1L)).thenReturn(Optional.of(new Importacao("v.csv", TipoImportacao.VENDAS)));
        when(importacoes.findById(2L)).thenReturn(Optional.of(new Importacao("r.csv", TipoImportacao.RECEBIMENTOS)));
        List<Lancamento> vendas = List.of(l(1, " a ", "10.00"), l(2, "B", "20"),
                l(3, "C", "30"), l(4, "D", "10"), l(5, "d", "15"), l(6, null, "5"));
        List<Lancamento> recebimentos = List.of(l(7, "A", "10"), l(8, "B", "18"),
                l(9, "D", "25"), l(10, "E", "40"), l(11, "  ", "7"));
        when(lancamentos.findAllByImportacao_Id(1L)).thenReturn(vendas);
        when(lancamentos.findAllByImportacao_Id(2L)).thenReturn(recebimentos);

        List<ResultadoConciliacao> resultado = service.conciliar(1L, 2L);

        assertEquals(7, resultado.size());
        assertEquals(2, resultado.stream().filter(r -> r.status() == StatusConciliacao.REFERENCIA_AUSENTE).count());
        assertTrue(resultado.stream().filter(r -> r.referencia() == null).allMatch(r -> r.diferenca() == null));
        assertEquals(StatusConciliacao.CONCILIADO, resultado.get(2).status());
        assertEquals(0, resultado.get(2).diferenca().compareTo(BigDecimal.ZERO));
        assertEquals(StatusConciliacao.DIVERGENTE, resultado.get(3).status());
        assertEquals(0, resultado.get(3).diferenca().compareTo(new BigDecimal("-2")));
        assertEquals(StatusConciliacao.VENDA_SEM_RECEBIMENTO, resultado.get(4).status());
        assertNull(resultado.get(4).valorRecebido());
        assertEquals(StatusConciliacao.REFERENCIA_DUPLICADA, resultado.get(5).status());
        assertEquals(0, resultado.get(5).valorVenda().compareTo(new BigDecimal("25")));
        assertEquals(StatusConciliacao.RECEBIMENTO_SEM_VENDA, resultado.get(6).status());
        assertNull(resultado.get(6).valorVenda());
        assertEquals(StatusLancamento.CONCILIADO, vendas.getFirst().getStatus());
        assertEquals(StatusLancamento.CONCILIADO, recebimentos.getFirst().getStatus());
        assertTrue(vendas.subList(1, vendas.size()).stream().allMatch(l -> l.getStatus() == StatusLancamento.DIVERGENTE));
        verify(lancamentos).saveAll(vendas);
        verify(lancamentos).saveAll(recebimentos);
    }

    @Test
    void rejeitaImportacaoInexistente() {
        when(importacoes.findById(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.conciliar(1L, 2L));
        verifyNoInteractions(lancamentos);
    }

    @Test
    void rejeitaTiposIncorretos() {
        when(importacoes.findById(1L)).thenReturn(Optional.of(new Importacao("v.csv", TipoImportacao.RECEBIMENTOS)));
        when(importacoes.findById(2L)).thenReturn(Optional.of(new Importacao("r.csv", TipoImportacao.RECEBIMENTOS)));
        assertThrows(IllegalArgumentException.class, () -> service.conciliar(1L, 2L));
        when(importacoes.findById(1L)).thenReturn(Optional.of(new Importacao("v.csv", TipoImportacao.VENDAS)));
        when(importacoes.findById(2L)).thenReturn(Optional.of(new Importacao("r.csv", TipoImportacao.VENDAS)));
        assertThrows(IllegalArgumentException.class, () -> service.conciliar(1L, 2L));
        verifyNoInteractions(lancamentos);
    }

    private Lancamento l(long id, String referencia, String valor) {
        return new Lancamento(id, null, 1, referencia, LocalDate.of(2026, 10, 13), "", new BigDecimal(valor));
    }
}
