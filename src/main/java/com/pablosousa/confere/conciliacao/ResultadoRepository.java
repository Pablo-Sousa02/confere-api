package com.pablosousa.confere.conciliacao;
import org.springframework.data.jpa.repository.*;
public interface ResultadoRepository extends JpaRepository<ResultadoPersistido, Long>, JpaSpecificationExecutor<ResultadoPersistido> {
    long countByConciliacao_IdAndConciliacao_EmpresaIdAndStatus(Long id, Long empresaId, StatusConciliacao status);
}
