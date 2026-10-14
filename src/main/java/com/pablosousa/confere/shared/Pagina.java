package com.pablosousa.confere.shared;
import org.springframework.data.domain.*;
import java.util.List;
public record Pagina<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> Pagina<T> de(Page<T> page) {
        return new Pagina<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
    public static Pageable pedido(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Página deve ser >= 0 e tamanho entre 1 e 100.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
