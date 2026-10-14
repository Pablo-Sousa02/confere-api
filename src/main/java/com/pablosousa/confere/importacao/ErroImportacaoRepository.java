package com.pablosousa.confere.importacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface ErroImportacaoRepository extends JpaRepository<ErroImportacao, Long> {
    long countByImportacao_IdAndImportacao_Empresa_Id(Long importacaoId, Long empresaId);
    List<ErroImportacao> findAllByImportacao_IdAndImportacao_Empresa_IdOrderByNumeroLinhaAsc(
            Long importacaoId, Long empresaId
    );
}
