package com.pablosousa.confere;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.security.SecureRandom;
import java.util.*;
public final class TestDatabase {
    private TestDatabase() {}
    public static String url() {
        String url = System.getenv("TEST_DB_URL");
        if (url == null) url = BancoContainer.POSTGRES.getJdbcUrl().split("\\?", 2)[0];
        return validar(url);
    }
    public static String validar(String url) {
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/confere_test[a-zA-Z0-9_]*"))
            throw new IllegalStateException("Defina TEST_DB_URL apontando para um banco PostgreSQL exclusivo chamado confere_test... (sem query params). Use scripts/test.ps1.");
        if (url.equals(System.getenv("DB_URL"))) throw new IllegalStateException("TEST_DB_URL não pode ser igual a DB_URL.");
        return url;
    }
    public static String usuario() { return System.getenv("TEST_DB_URL") == null ? BancoContainer.POSTGRES.getUsername() : Objects.requireNonNull(System.getenv("TEST_DB_USERNAME"), "Defina TEST_DB_USERNAME."); }
    public static String senha() { return System.getenv("TEST_DB_URL") == null ? BancoContainer.POSTGRES.getPassword() : System.getenv().getOrDefault("TEST_DB_PASSWORD", ""); }
    private static final class BancoContainer {
        static final org.testcontainers.postgresql.PostgreSQLContainer POSTGRES;
        static {
            POSTGRES = new org.testcontainers.postgresql.PostgreSQLContainer("postgres:18")
                    .withDatabaseName("confere_test").withUsername("confere_test").withPassword(segredo());
            POSTGRES.start();
        }
    }
    private static final class EmailContainer {
        static final org.testcontainers.containers.GenericContainer<?> MAIL;
        static { MAIL = new org.testcontainers.containers.GenericContainer<>("axllent/mailpit:latest")
                .withExposedPorts(1025,8025); MAIL.start(); }
    }
    public static String mailUrl() { return "http://" + EmailContainer.MAIL.getHost() + ":" + EmailContainer.MAIL.getMappedPort(8025); }
    public static String segredo() { byte[] bytes = new byte[48]; new SecureRandom().nextBytes(bytes); return Base64.getEncoder().encodeToString(bytes); }
    public static void configurar(DynamicPropertyRegistry registry) {
        String schema = "api_test_" + UUID.randomUUID().toString().replace("-", "");
        registry.add("spring.datasource.url", TestDatabase::url);
        registry.add("spring.datasource.username", TestDatabase::usuario);
        registry.add("spring.datasource.password", TestDatabase::senha);
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + schema);
        registry.add("app.jwt.secret", TestDatabase::segredo);
        registry.add("app.bootstrap.enabled", () -> false);
        registry.add("app.cors.origins", () -> "http://localhost:5173");
        registry.add("app.abuse.auth-per-minute", () -> 500);
        registry.add("app.abuse.upload-per-minute", () -> 500);
        registry.add("app.abuse.account-per-minute", () -> 500);
        registry.add("spring.mail.host", () -> EmailContainer.MAIL.getHost());
        registry.add("spring.mail.port", () -> EmailContainer.MAIL.getMappedPort(1025));
    }
}
