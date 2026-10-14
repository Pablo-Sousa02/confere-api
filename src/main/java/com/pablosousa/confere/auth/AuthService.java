package com.pablosousa.confere.auth;
import com.pablosousa.confere.empresa.*;
import com.pablosousa.confere.usuario.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AuthService {
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final String hashInexistente;
    private final com.pablosousa.confere.config.LimiteAbuso abuso;
    private final int limiteConta;
    public AuthService(EmpresaRepository empresas, UsuarioRepository usuarios, PasswordEncoder encoder, TokenService tokens,
                       com.pablosousa.confere.config.LimiteAbuso abuso,
                       @org.springframework.beans.factory.annotation.Value("${app.abuse.account-per-minute:10}") int limiteConta) {
        this.empresas = empresas; this.usuarios = usuarios; this.encoder = encoder; this.tokens = tokens;
        this.hashInexistente = encoder.encode(java.util.UUID.randomUUID().toString());
        this.abuso = abuso; this.limiteConta = limiteConta;
    }
    @Transactional
    public AuthDtos.Token cadastrar(AuthDtos.Cadastro dto) {
        String email = Credenciais.email(dto.email()), senha = Credenciais.senha(dto.senha());
        if (usuarios.existsByEmail(email)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "E-mail indisponível.");
        var empresa = empresas.save(new Empresa(Credenciais.nome(dto.empresa())));
        var usuario = usuarios.saveAndFlush(new Usuario(empresa, Credenciais.nome(dto.nome()), email, encoder.encode(senha), Papel.ADMIN));
        return tokens.emitir(usuario);
    }
    @Transactional(readOnly = true)
    public AuthDtos.Token login(AuthDtos.Login dto) {
        String email = Credenciais.email(dto.email());
        if (!abuso.permitir("login-conta:" + RecuperacaoService.hash(email), limiteConta, 60))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde um minuto.");
        var usuario = usuarios.findByEmail(email).orElse(null);
        boolean confere = dto.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
                && encoder.matches(dto.senha(), usuario == null ? hashInexistente : usuario.getSenhaHash());
        if (usuario == null || !confere || !usuario.isAtivo()) throw new BadCredentialsException("E-mail ou senha inválidos.");
        return tokens.emitir(usuario);
    }
}
