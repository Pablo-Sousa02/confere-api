package com.pablosousa.confere.auth;
import com.pablosousa.confere.usuario.UsuarioRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Component
public class AutenticacaoJwt implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UsuarioRepository usuarios;
    public AutenticacaoJwt(UsuarioRepository usuarios) { this.usuarios = usuarios; }
    @Override @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            var usuario = usuarios.findById(Long.valueOf(jwt.getSubject())).orElseThrow(this::invalido);
            Number empresa = jwt.getClaim("empresaId"), versao = jwt.getClaim("versao");
            if (!usuario.isAtivo() || empresa == null || versao == null
                    || !usuario.getEmpresa().getId().equals(empresa.longValue())
                    || usuario.getVersaoToken() != versao.longValue()
                    || !usuario.getPapel().name().equals(jwt.getClaimAsString("papel"))) throw invalido();
            return new UsernamePasswordAuthenticationToken(
                    new Identidade(usuario.getId(), usuario.getEmpresa().getId(), usuario.getPapel()), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPapel().name())));
        } catch (IllegalArgumentException | ClassCastException e) { throw invalido(); }
    }
    private OAuth2AuthenticationException invalido() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Token inválido.");
    }
}
