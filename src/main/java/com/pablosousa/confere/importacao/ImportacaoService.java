package com.pablosousa.confere.importacao;

import com.pablosousa.confere.lancamento.Lancamento;
import com.pablosousa.confere.lancamento.LancamentoRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ImportacaoService {

    private static final Set<String> CABECALHOS_OBRIGATORIOS =
            Set.of("referencia", "data", "descricao", "valor");

    private final ImportacaoRepository importacaoRepository;
    private final LancamentoRepository lancamentoRepository;
    private final ErroImportacaoRepository erroImportacaoRepository;

    public ImportacaoService(
            ImportacaoRepository importacaoRepository,
            LancamentoRepository lancamentoRepository,
            ErroImportacaoRepository erroImportacaoRepository
    ) {
        this.importacaoRepository = importacaoRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.erroImportacaoRepository = erroImportacaoRepository;
    }

    @Transactional
    public ImportacaoResultado importar(MultipartFile arquivo, TipoImportacao tipo) {
        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo está vazio.");
        }

        String nomeArquivo = arquivo.getOriginalFilename() == null
                ? "arquivo.csv"
                : arquivo.getOriginalFilename();

        if (!nomeArquivo.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Envie um arquivo com extensão .csv.");
        }

        Importacao importacao =
                importacaoRepository.save(new Importacao(nomeArquivo, tipo));

        List<Lancamento> lancamentosValidos = new ArrayList<>();
        List<ErroImportacao> erros = new ArrayList<>();

        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (Reader reader = new InputStreamReader(
                arquivo.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, formato)) {

            Map<String, Integer> cabecalhos = parser.getHeaderMap();

            if (cabecalhos == null || !cabecalhos.keySet().containsAll(CABECALHOS_OBRIGATORIOS)) {
                throw new IllegalArgumentException(
                        "O CSV precisa ter os cabeçalhos: referencia;data;descricao;valor"
                );
            }

            for (CSVRecord registro : parser) {
                int numeroLinha = (int) registro.getRecordNumber() + 1;

                try {
                    if (!registro.isConsistent()) {
                        throw new IllegalArgumentException("A quantidade de colunas está incorreta.");
                    }

                    String referencia = registro.get("referencia").trim();
                    String dataTexto = registro.get("data").trim();
                    String descricao = registro.get("descricao").trim();
                    String valorTexto = registro.get("valor").trim();

                    if (dataTexto.isBlank() || descricao.isBlank() || valorTexto.isBlank()) {
                        throw new IllegalArgumentException(
                                "Data, descrição e valor são obrigatórios."
                        );
                    }

                    LocalDate data;
                    try {
                        data = LocalDate.parse(dataTexto);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException(
                                "Data inválida. Use o formato AAAA-MM-DD."
                        );
                    }

                    BigDecimal valor = converterValor(valorTexto);

                    if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("O valor precisa ser maior que zero.");
                    }

                    lancamentosValidos.add(new Lancamento(
                            null,
                            importacao,
                            numeroLinha,
                            referencia,
                            data,
                            descricao,
                            valor
                    ));

                } catch (IllegalArgumentException e) {
                    erros.add(new ErroImportacao(
                            importacao,
                            numeroLinha,
                            e.getMessage()
                    ));
                }
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Não foi possível ler o CSV.", e);
        }

        lancamentoRepository.saveAll(lancamentosValidos);
        erroImportacaoRepository.saveAll(erros);

        StatusImportacao status = erros.isEmpty()
                ? StatusImportacao.CONCLUIDA
                : StatusImportacao.CONCLUIDA_COM_ERROS;

        importacao.setStatus(status);
        importacaoRepository.save(importacao);

        return new ImportacaoResultado(
                importacao.getId(),
                nomeArquivo,
                tipo,
                status,
                lancamentosValidos.size(),
                erros.size()
        );
    }

    @Transactional(readOnly = true)
    public List<ImportacaoResumo> listarImportacoes() {
        return importacaoRepository.findAll(
                        org.springframework.data.domain.Sort
                                .by(org.springframework.data.domain.Sort.Direction.DESC, "id")
                )
                .stream()
                .map(importacao -> new ImportacaoResumo(
                        importacao.getId(),
                        importacao.getNomeArquivo(),
                        importacao.getTipo(),
                        importacao.getStatus(),
                        lancamentoRepository.countByImportacao_Id(importacao.getId()),
                        erroImportacaoRepository.countByImportacao_Id(importacao.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ErroImportacaoResumo> listarErros(Long importacaoId) {
        if (!importacaoRepository.existsById(importacaoId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Importação não encontrada."
            );
        }

        return erroImportacaoRepository
                .findAllByImportacao_IdOrderByNumeroLinhaAsc(importacaoId)
                .stream()
                .map(erro -> new ErroImportacaoResumo(
                        erro.getNumeroLinha(),
                        erro.getMensagem()
                ))
                .toList();
    }

    private BigDecimal converterValor(String valorTexto) {
        try {
            String valorNormalizado = valorTexto.trim();

            if (valorNormalizado.contains(",")) {
                valorNormalizado = valorNormalizado
                        .replace(".", "")
                        .replace(",", ".");
            }

            return new BigDecimal(valorNormalizado);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Valor inválido. Exemplo de formato aceito: 120,00."
            );
        }

    }
}
