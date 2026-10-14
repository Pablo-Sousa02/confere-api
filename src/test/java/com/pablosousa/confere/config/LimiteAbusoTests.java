package com.pablosousa.confere.config;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class LimiteAbusoTests {
    @Test void limitaCadaChaveELiberaAposJanela() {
        AtomicLong agora = new AtomicLong();
        var limite = new LimiteAbuso(agora::get);
        assertTrue(limite.permitir("login-ip", 2, 60)); assertTrue(limite.permitir("login-ip", 2, 60));
        assertFalse(limite.permitir("login-ip", 2, 60)); assertTrue(limite.permitir("outro-ip", 2, 60));
        agora.addAndGet(60_000_000_000L); assertTrue(limite.permitir("login-ip", 2, 60));
    }
    @Test void limitaMemoriaSemEvictarBloqueiosAtivos() {
        var limite = new LimiteAbuso(() -> 0L);
        for (int i=0; i<10000; i++) assertTrue(limite.permitir("ip"+i, 1, 60));
        assertFalse(limite.permitir("nova-chave", 1, 60)); assertFalse(limite.permitir("ip0", 1, 60));
    }
}
