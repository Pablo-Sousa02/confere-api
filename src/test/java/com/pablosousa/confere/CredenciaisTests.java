package com.pablosousa.confere;
import com.pablosousa.confere.auth.Credenciais;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CredenciaisTests {
    @Test void normalizaEmailERejeitaInvalidos() {
        assertEquals("ana@example.com", Credenciais.email(" ANA@Example.COM "));
        for (String email : new String[]{"sem-arroba", "a@", "a b@example.com"})
            assertThrows(IllegalArgumentException.class, () -> Credenciais.email(email));
    }
    @Test void respeitaLimiteDeBytesDoBCrypt() {
        assertThrows(IllegalArgumentException.class, () -> Credenciais.senha("á".repeat(37)));
        assertEquals(36, Credenciais.senha("á".repeat(36)).length());
        assertThrows(IllegalArgumentException.class, () -> Credenciais.senha(UUID.randomUUID().toString().substring(0, 11)));
    }
    @Test void bancoDeTesteRecusaAusenciaOuUrlDeDesenvolvimento() {
        assertThrows(IllegalStateException.class, () -> TestDatabase.validar(null));
        assertThrows(IllegalStateException.class, () -> TestDatabase.validar("jdbc:postgresql://localhost:5432/confere_db"));
        assertThrows(IllegalStateException.class, () -> TestDatabase.validar("jdbc:postgresql://localhost:5432/confere_test?currentSchema=public"));
        assertTrue(TestDatabase.validar("jdbc:postgresql://localhost:55433/confere_test").contains("/confere_test"));
    }
}
