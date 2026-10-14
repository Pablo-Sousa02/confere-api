package com.pablosousa.confere.auth;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
public final class Credenciais {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private Credenciais() {}
    public static String email(String email) {
        String normalizado = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalizado.length() > 254 || !EMAIL.matcher(normalizado).matches())
            throw new IllegalArgumentException("E-mail inválido.");
        return normalizado;
    }
    public static String senha(String senha) {
        if (senha == null || senha.isBlank() || senha.length() < 12 || senha.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Senha deve ter pelo menos 12 caracteres e no máximo 72 bytes em UTF-8.");
        return senha;
    }
    public static String nome(String nome) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 150)
            throw new IllegalArgumentException("Nome deve ter entre 1 e 150 caracteres.");
        return nome.strip();
    }
}
