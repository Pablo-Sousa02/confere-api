package com.pablosousa.confere.config;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pablosousa.confere.auth.AutenticacaoJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecretKey jwtKey(@Value("${app.jwt.secret}") String encoded) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("JWT_SECRET deve ser Base64."); }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET precisa conter pelo menos 32 bytes aleatórios em Base64.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key, @Value("${app.jwt.issuer}") String issuer) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), jwt ->
                jwt.getExpiresAt() != null && jwt.getAudience().contains("confere-api")
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<ProtecaoAbusoFilter> registroAbuso(ProtecaoAbusoFilter filtro) {
        var registro = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filtro); registro.setEnabled(false); return registro;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, AutenticacaoJwt converter, ProtecaoAbusoFilter abuso,
            @org.springframework.beans.factory.annotation.Qualifier("cors") CorsConfigurationSource corsSource) throws Exception {
        return http.csrf(c -> c.disable()).cors(c -> c.configurationSource(corsSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/cadastro", "/api/auth/login", "/api/auth/recuperar-senha", "/api/auth/redefinir-senha").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> com.pablosousa.confere.shared.ApiExceptionHandler.escreverErro(res, req, 401, "Autenticação necessária ou token inválido."))
                        .accessDeniedHandler((req, res, ex) -> com.pablosousa.confere.shared.ApiExceptionHandler.escreverErro(res, req, 403, "Acesso não permitido.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> com.pablosousa.confere.shared.ApiExceptionHandler.escreverErro(res, req, 401, "Autenticação necessária ou token inválido."))
                        .accessDeniedHandler((req, res, ex) -> com.pablosousa.confere.shared.ApiExceptionHandler.escreverErro(res, req, 403, "Acesso não permitido.")))
                .addFilterAfter(abuso, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
                .build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.cors.origins:}") String origens) {
        var config = new CorsConfiguration();
        var lista = Arrays.stream(origens.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (lista.stream().anyMatch(s -> s.contains("*") || !(s.startsWith("https://") || s.startsWith("http://"))))
            throw new IllegalStateException("CORS exige origens HTTP(S) explícitas, sem curingas.");
        config.setAllowedOrigins(lista);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("X-Conciliacao-Id", "Location"));
        config.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;
    }
}
