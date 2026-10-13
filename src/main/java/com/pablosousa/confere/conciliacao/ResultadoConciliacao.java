package com.pablosousa.confere.conciliacao;

import java.math.BigDecimal;

public record ResultadoConciliacao(
        String referencia,
        BigDecimal valorVenda,
        BigDecimal valorRecebido,
        BigDecimal diferenca,
        StatusConciliacao status
) {
}
