package com.pablosousa.confere.config;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.LongSupplier;

@Component
public class LimiteAbuso {
    private record Janela(long inicio, int acessos) {}
    private final Map<String, Janela> janelas = new HashMap<>();
    private final LongSupplier relogio;
    public LimiteAbuso() { this(System::nanoTime); }
    public LimiteAbuso(LongSupplier relogio) { this.relogio = relogio; }
    public synchronized boolean permitir(String chave, int maximo, int segundos) {
        if (maximo < 1 || segundos < 1) throw new IllegalArgumentException("Limites de abuso precisam ser positivos.");
        long agora = relogio.getAsLong(), duracao = segundos * 1_000_000_000L;
        janelas.entrySet().removeIf(e -> agora - e.getValue().inicio() >= duracao);
        var janela = janelas.get(chave);
        if (janela == null && janelas.size() >= 10000) return false;
        if (janela != null && janela.acessos() >= maximo) return false;
        janelas.put(chave, new Janela(janela == null ? agora : janela.inicio(), janela == null ? 1 : janela.acessos() + 1));
        return true;
    }
}
