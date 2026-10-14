package com.pablosousa.confere.auth;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/cadastro") @ResponseStatus(HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @Operation(summary="Cria uma empresa e seu administrador", security={})
    public AuthDtos.Token cadastrar(@Valid @RequestBody AuthDtos.Cadastro dto) { return service.cadastrar(dto); }
    @PostMapping("/login") @Operation(summary="Obtém token Bearer", security={})
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public AuthDtos.Token login(@Valid @RequestBody AuthDtos.Login dto) { return service.login(dto); }
}
