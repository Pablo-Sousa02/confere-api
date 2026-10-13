package com.pablosousa.confere.conciliacao;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conciliacoes")
public class ConciliacaoController {

    private final ConciliacaoService conciliacaoService;

    public ConciliacaoController(ConciliacaoService conciliacaoService) {
        this.conciliacaoService = conciliacaoService;
    }

    @PostMapping
    public List<ResultadoConciliacao> conciliar(
            @RequestParam("importacaoVendasId") Long importacaoVendasId,
            @RequestParam("importacaoRecebimentosId") Long importacaoRecebimentosId
    ) {
        return conciliacaoService.conciliar(
                importacaoVendasId,
                importacaoRecebimentosId
        );
    }
}
