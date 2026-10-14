package com.pablosousa.confere.importacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Importacao> {
    java.util.Optional<Importacao> findByIdAndEmpresa_Id(Long id, Long empresaId);
}
