package com.pablosousa.confere.auth;

import com.pablosousa.confere.usuario.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class RecuperacaoService {
    private final UsuarioRepository usuarios;
    private final RecuperacaoRepository recuperacoes;
    private final PasswordEncoder encoder;
    private final EmailRecuperacao email;
    public RecuperacaoService(UsuarioRepository usuarios, RecuperacaoRepository recuperacoes, PasswordEncoder encoder, EmailRecuperacao email) {
        this.usuarios = usuarios; this.recuperacoes = recuperacoes; this.encoder = encoder; this.email = email;
    }
    @Transactional
    public void solicitar(String endereco) {
        String normalizado = Credenciais.email(endereco);
        if (!email.disponivel()) return;
        var usuario = usuarios.findByEmail(normalizado).orElse(null);
        if (usuario == null || !usuario.isAtivo()) return;
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        recuperacoes.saveAndFlush(new RecuperacaoSenha(usuario.getId(), hash(token), Instant.now().plusSeconds(1200), usuario.getVersaoToken()));
        try { email.enviar(usuario.getEmail(), token); }
        catch (org.springframework.mail.MailException e) {
            // Reverte o token sem expor conta, corpo do e-mail ou exceção SMTP ao cliente/log.
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
    @Transactional
    public void redefinir(String token, String novaSenha) {
        Credenciais.senha(novaSenha);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalido();
        var recuperacao = recuperacoes.bloquear(hash(token)).orElseThrow(this::invalido);
        var usuario = usuarios.bloquear(recuperacao.getUsuarioId()).orElseThrow(this::invalido);
        Instant agora = Instant.now();
        if (!recuperacao.disponivel(agora) || !usuario.isAtivo() || usuario.getVersaoToken() != recuperacao.getVersaoUsuario()) throw invalido();
        usuario.redefinirSenha(encoder.encode(novaSenha));
        recuperacao.consumir(agora);
    }
    private IllegalArgumentException invalido() { return new IllegalArgumentException("Link de recuperação inválido ou expirado."); }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 indisponível."); }
    }
}
