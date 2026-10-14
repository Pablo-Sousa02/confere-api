package com.pablosousa.confere.auth;
import com.pablosousa.confere.usuario.Papel;
public record Identidade(Long usuarioId, Long empresaId, Papel papel) {}
