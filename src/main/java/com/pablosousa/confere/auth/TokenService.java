package com.pablosousa.confere.auth;
import com.pablosousa.confere.usuario.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttl;
    public TokenService(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer,
                        @Value("${app.jwt.ttl-seconds}") long ttl) {
        if (ttl < 60 || ttl > 3600) throw new IllegalArgumentException("TTL JWT deve estar entre 60 e 3600 segundos.");
        this.encoder = encoder; this.issuer = issuer; this.ttl = ttl;
    }
    public AuthDtos.Token emitir(Usuario usuario) {
        Instant agora = Instant.now(), expira = agora.plusSeconds(ttl);
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(usuario.getId().toString())
                .audience(java.util.List.of("confere-api")).issuedAt(agora).expiresAt(expira)
                .claim("empresaId", usuario.getEmpresa().getId()).claim("papel", usuario.getPapel().name())
                .claim("versao", usuario.getVersaoToken()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthDtos.Token(token, "Bearer", expira);
    }
}
