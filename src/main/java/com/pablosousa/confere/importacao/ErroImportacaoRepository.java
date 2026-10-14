package com.pablosousa.confere.importacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;


public interface ErroImportacaoRepository extends JpaRepository<ErroImportacao, Long> {
    long countByImportacao_Id(Long importacaoId);
    List<ErroImportacao> findAllByImportacao_IdOrderByNumeroLinhaAsc(
            Long importacaoId
    );
}