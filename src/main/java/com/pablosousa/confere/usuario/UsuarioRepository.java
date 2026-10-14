package com.pablosousa.confere.usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.id=:id")
    Optional<Usuario> bloquear(Long id);
    Optional<Usuario> findByEmail(String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findByIdAndEmpresa_Id(Long id, Long empresaId);
    boolean existsByEmail(String email);
    boolean existsByEmpresa_Id(Long empresaId);
    long countByEmpresa_IdAndPapelAndAtivoTrue(Long empresaId, Papel papel);
    Page<Usuario> findAllByEmpresa_Id(Long empresaId, Pageable pageable);
}
