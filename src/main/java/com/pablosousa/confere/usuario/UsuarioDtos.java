package com.pablosousa.confere.usuario;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public final class UsuarioDtos {
    private UsuarioDtos() {}
    public record Criar(@NotBlank @Size(max=150) String nome, @NotBlank @Size(max=254) String email,
                        @NotBlank @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String senha, @NotNull Papel papel) {}
    public record Atualizar(@NotBlank @Size(max=150) String nome, @NotNull Papel papel, @NotNull Boolean ativo,
                            @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String senha) {}
    public record Resumo(Long id, String nome, String email, Papel papel, boolean ativo) {
        public static Resumo de(Usuario u) { return new Resumo(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.isAtivo()); }
    }
}
