package com.pablosousa.confere.lancamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {
    List<Lancamento> findAllByImportacao_IdAndImportacao_Empresa_Id(Long importacaoId, Long empresaId);
    long countByImportacao_IdAndImportacao_Empresa_Id(Long importacaoId, Long empresaId);
}
