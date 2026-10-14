package com.pablosousa.confere.empresa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.inicial = true")
    Optional<Empresa> bloquearInicial();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.id = :id")
    Optional<Empresa> bloquear(Long id);
}
