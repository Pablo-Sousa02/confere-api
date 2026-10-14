package com.pablosousa.confere.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RenderDatabaseEnvironmentPostProcessorTests {
    @Test void converteUrlPrivadaSemCredenciaisNaUrlJdbc() {
        String password = UUID.randomUUID() + "+:@/";
        String encoded = java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        var connection = RenderDatabaseEnvironmentPostProcessor.parse("postgresql://confere:" + encoded + "@db.internal:5432/confere", "require");
        assertEquals("jdbc:postgresql://db.internal:5432/confere?sslmode=require", connection.jdbcUrl());
        assertTrue(password.equals(connection.password()), "Credencial deve ser decodificada sem alterar caracteres.");
        assertFalse(connection.toString().contains(password));
    }
    @Test void naoExpoeUrlInvalidaNemAceitaParametrosQueSobrescrevemCredenciais() {
        String password = UUID.randomUUID().toString();
        for (String suffix : List.of("?password=x", "?sslmode=disable", "?sslmode=require&user=x", "#fragment")) {
            String source = "postgres://confere:" + password + "@db.internal/confere" + suffix;
            var error = assertThrows(IllegalStateException.class, () -> RenderDatabaseEnvironmentPostProcessor.parse(source, "require"));
            assertFalse(error.getMessage().contains(password)); assertNull(error.getCause());
        }
    }
    @Test void preservaJdbcExplicitoEPermiteSomenteLoopbackSemTls() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/confere_test")
                .withProperty("DATABASE_URL", "invalida");
        new RenderDatabaseEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        assertFalse(environment.getPropertySources().contains("render-database"));
        var local = RenderDatabaseEnvironmentPostProcessor.parse("postgres://u:" + UUID.randomUUID() + "@127.0.0.1/confere_test?sslmode=disable", "require");
        assertTrue(local.jdbcUrl().endsWith("sslmode=disable"));
    }
    @Test void segredoBase64De256BitsGeradoPeloRenderEhAceito() {
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        var key = new SecurityConfig().jwtKey(Base64.getEncoder().encodeToString(bytes));
        assertEquals(32, key.getEncoded().length);
    }
}
