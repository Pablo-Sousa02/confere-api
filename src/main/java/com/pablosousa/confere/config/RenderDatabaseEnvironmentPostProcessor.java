package com.pablosousa.confere.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Adapta a URL privada do Render sem deixar credenciais na URL JDBC/logs do pool. */
public final class RenderDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Preserva a configuração JDBC explícita e o ambiente de teste isolado.
        if (!environment.getProperty("spring.datasource.url", "").isBlank()) return;
        String source = environment.getProperty("DATABASE_URL", "");
        if (source.isBlank()) return;
        var connection = parse(source, environment.getProperty("DATABASE_SSL_MODE", "require"));
        environment.getPropertySources().addFirst(new MapPropertySource("render-database", Map.of(
                "spring.datasource.url", connection.jdbcUrl(),
                "spring.datasource.username", connection.username(),
                "spring.datasource.password", connection.password())));
    }

    static Connection parse(String source, String defaultSslMode) {
        try {
            URI uri = new URI(source);
            if (!Set.of("postgres", "postgresql").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getFragment() != null || uri.getRawUserInfo() == null) throw invalid();
            String[] credentials = uri.getRawUserInfo().split(":", 2);
            if (credentials.length != 2) throw invalid();
            String username = decode(credentials[0]), password = decode(credentials[1]);
            String path = uri.getPath();
            if (username.isBlank() || password.isEmpty() || path == null || !path.matches("/[A-Za-z0-9_-]+")
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw invalid();
            String sslMode = defaultSslMode;
            if (uri.getRawQuery() != null) {
                String[] parameter = uri.getRawQuery().split("=", -1);
                if (parameter.length != 2 || !parameter[0].equals("sslmode")) throw invalid();
                sslMode = parameter[1];
            }
            if (!Set.of("require", "verify-ca", "verify-full", "disable").contains(sslMode)) throw invalid();
            if (sslMode.equals("disable") && !Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())) throw invalid();
            String clean = new URI("postgresql", null, uri.getHost(), uri.getPort() == -1 ? 5432 : uri.getPort(), path, null, null).toASCIIString();
            return new Connection("jdbc:" + clean + "?sslmode=" + sslMode, username, password);
        } catch (Exception e) {
            // Não encadeia URISyntaxException: sua mensagem pode conter a URL inteira com senha.
            throw invalid();
        }
    }

    private static String decode(String value) { return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8); }
    private static IllegalStateException invalid() {
        return new IllegalStateException("DATABASE_URL inválida. Use URL postgres/postgresql com credenciais e banco; TLS require por padrão. Parâmetro aceito: sslmode. Não exiba a URL para diagnóstico.");
    }
    static final class Connection {
        private final String jdbcUrl, username, password;
        Connection(String jdbcUrl, String username, String password) { this.jdbcUrl = jdbcUrl; this.username = username; this.password = password; }
        String jdbcUrl() { return jdbcUrl; }
        String username() { return username; }
        String password() { return password; }
        @Override public String toString() { return "PostgreSQL connection [redacted]"; }
    }
}
