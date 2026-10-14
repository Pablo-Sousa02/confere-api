package com.pablosousa.confere;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RenderStartupTests {
    @Test void iniciaComUrlRenderSemSmtpFrontendOuSwaggerEVerificaBancoNaReadiness() throws Exception {
        URI jdbc = URI.create(TestDatabase.url().substring("jdbc:".length()));
        String encodeUser = URLEncoder.encode(TestDatabase.usuario(), java.nio.charset.StandardCharsets.UTF_8);
        String encodePassword = URLEncoder.encode(TestDatabase.senha(), java.nio.charset.StandardCharsets.UTF_8);
        String databaseUrl = "postgresql://" + encodeUser + ":" + encodePassword + "@" + jdbc.getHost() + ":" + jdbc.getPort() + jdbc.getPath() + "?sslmode=disable";
        String schema = "render_test_" + UUID.randomUUID().toString().replace("-", "");
        var properties = new HashMap<String,Object>();
        properties.put("spring.profiles.active", "prod,render");
        properties.put("DATABASE_URL", databaseUrl);
        properties.put("spring.datasource.url", ""); properties.put("DB_URL", "");
        properties.put("JWT_SECRET", TestDatabase.segredo()); properties.put("PORT", "0");
        properties.put("SMTP_HOST", ""); properties.put("SMTP_USERNAME", ""); properties.put("SMTP_PASSWORD", "");
        properties.put("RESET_PASSWORD_URL", ""); properties.put("EMAIL_FROM", "");
        properties.put("PASSWORD_RESET_ENABLED", "false"); properties.put("API_DOCS_ENABLED", "false");
        properties.put("CORS_ALLOWED_ORIGINS", ""); properties.put("BOOTSTRAP_ENABLED", "false");
        properties.put("spring.flyway.schemas", schema); properties.put("spring.flyway.default-schema", schema);
        properties.put("spring.jpa.properties.hibernate.default_schema", schema);
        properties.put("spring.datasource.hikari.connection-init-sql", "SET search_path TO " + schema);
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("render-test-input", properties));
        try (var context = new SpringApplicationBuilder(ConfereApplication.class).environment(environment).run()) {
            var web = (org.springframework.boot.web.server.context.WebServerApplicationContext) context;
            String base = "http://localhost:" + web.getWebServer().getPort();
            var client = HttpClient.newHttpClient();
            var mapper = JsonMapper.builder().build();
            var health = client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health/readiness")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode()); assertEquals("UP", mapper.readTree(health.body()).get("status").asText());
            assertFalse(mapper.readTree(health.body()).has("components"));
            assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/api/importacoes")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            for (String path : List.of("/v3/api-docs", "/swagger-ui/index.html"))
                assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health/db")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            var preflight = HttpRequest.newBuilder(URI.create(base + "/api/importacoes")).header("Origin", "https://frontend.example")
                    .header("Access-Control-Request-Method", "GET").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(403, client.send(preflight, HttpResponse.BodyHandlers.discarding()).statusCode());
            String email = UUID.randomUUID() + "@example.com";
            byte[] body = mapper.writeValueAsBytes(Map.of("empresa", "Demo", "nome", "Admin", "email", email, "senha", UUID.randomUUID().toString()));
            var cadastro = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/cadastro")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(201, cadastro.statusCode());
            var request = HttpRequest.newBuilder(URI.create(base + "/api/auth/recuperar-senha")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(Map.of("email",email)))).build();
            assertEquals(202, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            var db = new JdbcTemplate(context.getBean(javax.sql.DataSource.class));
            assertEquals(0, db.queryForObject("select count(*) from recuperacoes_senha", Integer.class));
            assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            var pool = (HikariDataSource) context.getBean(javax.sql.DataSource.class);
            assertFalse(pool.getJdbcUrl().contains(TestDatabase.senha()));
            pool.close();
            var down = client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health/readiness")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, down.statusCode()); assertFalse(mapper.readTree(down.body()).has("components"));
        }
    }
}
