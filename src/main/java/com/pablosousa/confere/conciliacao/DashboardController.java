package com.pablosousa.confere.conciliacao;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/dashboard")
public class DashboardController {
    private final ConciliacaoService service;
    public DashboardController(ConciliacaoService service) { this.service = service; }
    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary="Resumo da empresa por execução ou período",
            description="Sem filtros mantém a última execução. historico=true ou desde/ate consolida a última execução de cada par de importações no período de criação; não soma reexecuções do mesmo par. Sinaliza importações usadas em pares diferentes.")
    public ConciliacaoService.Dashboard resumo(@RequestParam(required=false) Long conciliacaoId,
            @RequestParam(required=false) java.time.Instant desde, @RequestParam(required=false) java.time.Instant ate,
            @RequestParam(defaultValue="false") boolean historico) {
        if (conciliacaoId != null && (historico || desde != null || ate != null)) throw new IllegalArgumentException("Use uma execução ou um período, não ambos.");
        if (historico || desde != null || ate != null) return service.dashboardPeriodo(desde, ate);
        return service.dashboard(conciliacaoId);
    }
}
