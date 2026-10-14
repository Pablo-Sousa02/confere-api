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
    @io.swagger.v3.oas.annotations.Operation(summary="Concilia vendas e recebimentos e salva snapshot",
            description="Exige as duas importações da empresa autenticada e tipos corretos. Permite parciais distintos; repetições suspeitas exigem revisão. Retorna lista e X-Conciliacao-Id; repetições criam novo histórico.")
    public org.springframework.http.ResponseEntity<List<ResultadoConciliacao>> conciliar(
            @RequestParam("importacaoVendasId") Long importacaoVendasId,
            @RequestParam("importacaoRecebimentosId") Long importacaoRecebimentosId
    ) {
        var execucao = conciliacaoService.conciliar(
                importacaoVendasId,
                importacaoRecebimentosId
        );
        return org.springframework.http.ResponseEntity.ok().header("X-Conciliacao-Id", execucao.id().toString())
                .header("Location", "/api/conciliacoes/" + execucao.id()).body(execucao.resultados());
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary="Lista histórico da empresa", description="Filtra por importacaoId e período de criação desde/ate (instantes ISO, inclusivos).")
    public com.pablosousa.confere.shared.Pagina<ConciliacaoResumo> listar(
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) Long importacaoId,
            @RequestParam(required=false) java.time.Instant desde, @RequestParam(required=false) java.time.Instant ate) {
        return conciliacaoService.listar(page, size, importacaoId, desde, ate);
    }
    @GetMapping("/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary="Consulta metadados de uma conciliação da empresa")
    public ConciliacaoResumo obter(@PathVariable Long id) { return conciliacaoService.obter(id); }
    @GetMapping("/{id}/resultados")
    @io.swagger.v3.oas.annotations.Operation(summary="Consulta resultados persistidos", description="Página com filtros de status e referência exata normalizada.")
    public com.pablosousa.confere.shared.Pagina<ResultadoConciliacao> resultados(@PathVariable Long id,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) StatusConciliacao status, @RequestParam(required=false) String referencia) {
        return conciliacaoService.resultados(id, page, size, status, referencia);
    }
}
