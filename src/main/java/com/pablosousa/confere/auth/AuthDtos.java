package com.pablosousa.confere.auth;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
public final class AuthDtos {
    private AuthDtos() {}
    public record Cadastro(@NotBlank @Size(max=150) String empresa,
                           @NotBlank @Size(max=150) String nome,
                           @NotBlank @Size(max=254) String email,
                           @NotBlank @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String senha) {}
    public record Login(@NotBlank @Size(max=254) String email,
                        @NotBlank @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String senha) {}
    public record Token(String accessToken, String tokenType, Instant expiresAt) {}
}
