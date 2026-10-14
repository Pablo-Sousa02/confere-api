package com.pablosousa.confere.auth;
import com.pablosousa.confere.empresa.EmpresaRepository;
import com.pablosousa.confere.usuario.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @ConditionalOnProperty(name="app.bootstrap.enabled", havingValue="true")
public class BootstrapAdmin implements ApplicationRunner {
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String nome, email, senha;
    public BootstrapAdmin(EmpresaRepository empresas, UsuarioRepository usuarios, PasswordEncoder encoder,
                          @Value("${app.bootstrap.nome}") String nome, @Value("${app.bootstrap.email}") String email,
                          @Value("${app.bootstrap.senha}") String senha) {
        this.empresas = empresas; this.usuarios = usuarios; this.encoder = encoder;
        this.nome = nome; this.email = email; this.senha = senha;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        var empresa = empresas.bloquearInicial().orElseThrow(() -> new IllegalStateException("Empresa inicial ausente."));
        if (usuarios.existsByEmpresa_Id(empresa.getId())) return;
        var normalizado = Credenciais.email(email);
        if (usuarios.existsByEmail(normalizado)) throw new IllegalStateException("E-mail de bootstrap indisponível.");
        usuarios.saveAndFlush(new Usuario(empresa, Credenciais.nome(nome), normalizado,
                encoder.encode(Credenciais.senha(senha)), Papel.ADMIN));
    }
}
