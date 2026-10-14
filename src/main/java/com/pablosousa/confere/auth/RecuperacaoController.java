package com.pablosousa.confere.auth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;
@RestController @RequestMapping("/api/auth")
public class RecuperacaoController {
    private final RecuperacaoService service;
    public RecuperacaoController(RecuperacaoService service) { this.service = service; }
    public record Solicitar(@NotBlank @Size(max=254) String email) {}
    public record Redefinir(@NotBlank @Size(max=100) @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String token,
                            @NotBlank @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String senha) {}
    @PostMapping("/recuperar-senha") @ResponseStatus(HttpStatus.ACCEPTED) @SecurityRequirements
    @Operation(summary="Solicita recuperação de senha", description="Resposta genérica para contas existentes ou não. Link enviado por SMTP; nenhum token é devolvido pela API.")
    public Map<String,String> solicitar(@Valid @RequestBody Solicitar dto) {
        service.solicitar(dto.email()); return Map.of("message", "Se a conta estiver disponível, as instruções serão enviadas por e-mail.");
    }
    @PostMapping("/redefinir-senha") @ResponseStatus(HttpStatus.NO_CONTENT) @SecurityRequirements
    @Operation(summary="Redefine senha com token de uso único", description="O token expira em 20 minutos; redefine a senha e invalida tokens JWT e outros links anteriores.")
    public void redefinir(@Valid @RequestBody Redefinir dto) { service.redefinir(dto.token(), dto.senha()); }
}
