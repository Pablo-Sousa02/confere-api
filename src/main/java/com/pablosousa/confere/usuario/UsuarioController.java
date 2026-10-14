package com.pablosousa.confere.usuario;
import com.pablosousa.confere.shared.Pagina;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService service;
    public UsuarioController(UsuarioService service) { this.service = service; }
    @GetMapping @io.swagger.v3.oas.annotations.Operation(summary="Lista usuários da própria empresa (ADMIN)")
    public Pagina<UsuarioDtos.Resumo> listar(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.listar(page, size);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.Operation(summary="Cria usuário ADMIN ou ANALISTA na própria empresa (ADMIN)")
    public UsuarioDtos.Resumo criar(@Valid @RequestBody UsuarioDtos.Criar dto) { return service.criar(dto); }
    @PutMapping("/{id}") @io.swagger.v3.oas.annotations.Operation(summary="Altera nome, papel, situação e senha opcional (ADMIN)", description="Invalida tokens anteriores do usuário; preserva ao menos um administrador ativo.")
    public UsuarioDtos.Resumo atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioDtos.Atualizar dto) {
        return service.atualizar(id, dto);
    }
}
