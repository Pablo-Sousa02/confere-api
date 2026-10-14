package com.pablosousa.confere.config;

import com.pablosousa.confere.auth.*;
import com.pablosousa.confere.shared.ApiExceptionHandler;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class ProtecaoAbusoFilter extends OncePerRequestFilter {
    private final LimiteAbuso limite;
    private final int authMax, uploadMax;
    public ProtecaoAbusoFilter(LimiteAbuso limite, @Value("${app.abuse.auth-per-minute:15}") int authMax,
                               @Value("${app.abuse.upload-per-minute:10}") int uploadMax) {
        this.limite = limite; this.authMax = authMax; this.uploadMax = uploadMax;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        String path = req.getServletPath();
        if ("POST".equals(req.getMethod()) && (path.startsWith("/api/auth/") || path.equals("/api/importacoes"))) {
            boolean upload = path.equals("/api/importacoes");
            String chave = path + ":" + RecuperacaoService.hash(req.getRemoteAddr());
            boolean permitido = limite.permitir(chave, upload ? uploadMax * 3 : authMax, 60);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (upload && auth != null && auth.getPrincipal() instanceof Identidade identidade)
                permitido &= limite.permitir("upload-user:" + identidade.usuarioId(), uploadMax, 60);
            if (!permitido) {
                res.setHeader("Retry-After", "60"); ApiExceptionHandler.escreverErro(res, req, 429, "Muitas tentativas. Aguarde um minuto."); return;
            }
            if (!upload && req.getContentLengthLong() > 16384) {
                ApiExceptionHandler.escreverErro(res, req, 413, "Requisição de autenticação excede 16 KiB."); return;
            }
        }
        chain.doFilter(req, res);
    }
}
