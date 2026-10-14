package com.pablosousa.confere.auth;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface RecuperacaoRepository extends JpaRepository<RecuperacaoSenha, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecuperacaoSenha r where r.tokenHash=:hash")
    Optional<RecuperacaoSenha> bloquear(String hash);
}
