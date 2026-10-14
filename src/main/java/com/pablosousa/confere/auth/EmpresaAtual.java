package com.pablosousa.confere.auth;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
@Component
public class EmpresaAtual {
    public Identidade identidade() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Identidade identidade))
            throw new AuthenticationCredentialsNotFoundException("Autenticação necessária.");
        return identidade;
    }
    public Long id() { return identidade().empresaId(); }
}
