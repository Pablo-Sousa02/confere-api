package com.pablosousa.confere.importacao;

import com.pablosousa.confere.lancamento.Lancamento;
import com.pablosousa.confere.lancamento.LancamentoRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.pablosousa.confere.auth.EmpresaAtual;
import com.pablosousa.confere.empresa.EmpresaRepository;
import com.pablosousa.confere.shared.*;
import org.springframework.data.jpa.domain.Specification;

@Service
public class ImportacaoService {

    private static final Set<String> CABECALHOS_OBRIGATORIOS =
            Set.of("referencia", "data", "descricao", "valor");

    private final ImportacaoRepository importacaoRepository;
    private final LancamentoRepository lancamentoRepository;
    private final ErroImportacaoRepository erroImportacaoRepository;
    private final EmpresaAtual empresaAtual;
    private final EmpresaRepository empresaRepository;

    public ImportacaoService(
            ImportacaoRepository importacaoRepository,
            LancamentoRepository lancamentoRepository,
            ErroImportacaoRepository erroImportacaoRepository,
            EmpresaAtual empresaAtual,
            EmpresaRepository empresaRepository
    ) {
        this.importacaoRepository = importacaoRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.erroImportacaoRepository = erroImportacaoRepository;
        this.empresaAtual = empresaAtual;
        this.empresaRepository = empresaRepository;
    }

    @Transactional
    public ImportacaoResultado importar(MultipartFile arquivo, TipoImportacao tipo) {
        String conteudo = validarArquivo(arquivo);
        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo está vazio.");
        }

        String nomeArquivo = arquivo.getOriginalFilename() == null
                ? "arquivo.csv"
                : arquivo.getOriginalFilename();

        nomeArquivo = nomeArquivo.replace('\\', '/');
        nomeArquivo = nomeArquivo.substring(nomeArquivo.lastIndexOf('/') + 1);
        if (nomeArquivo.length() > 255 || nomeArquivo.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Nome de arquivo inválido.");
        }
        if (!nomeArquivo.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Envie um arquivo com extensão .csv.");
        }

        Importacao importacao =
                importacaoRepository.save(new Importacao(nomeArquivo, tipo,
                        empresaRepository.getReferenceById(empresaAtual.id())));

        List<Lancamento> lancamentosValidos = new ArrayList<>();
        List<ErroImportacao> erros = new ArrayList<>();

        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (Reader reader = new java.io.StringReader(conteudo);
             CSVParser parser = CSVParser.parse(reader, formato)) {

            Map<String, Integer> cabecalhos = parser.getHeaderMap();

            if (cabecalhos == null || !cabecalhos.keySet().containsAll(CABECALHOS_OBRIGATORIOS)) {
                throw new IllegalArgumentException(
                        "O CSV precisa ter os cabeçalhos: referencia;data;descricao;valor"
                );
            }

            for (CSVRecord registro : parser) {
                if (registro.getRecordNumber() > 20000) throw new IllegalArgumentException("Limite de 20000 registros por arquivo excedido.");
                int numeroLinha = (int) registro.getRecordNumber() + 1;

                try {
                    if (!registro.isConsistent()) {
                        throw new IllegalArgumentException("A quantidade de colunas está incorreta.");
                    }

                    String referencia = registro.get("referencia").trim();
                    String dataTexto = registro.get("data").trim();
                    String descricao = registro.get("descricao").trim();
                    String valorTexto = registro.get("valor").trim();
                    if (referencia.length() > 100 || descricao.length() > 255) {
                        throw new IllegalArgumentException("Referência deve ter até 100 caracteres e descrição até 255.");
                    }

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

                    if (valorTexto.length() > 100) throw new IllegalArgumentException("Campo valor deve ter até 100 caracteres.");
                    BigDecimal valor = converterValor(valorTexto);
                    if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("O valor precisa ser maior que zero.");
                    }
                    // Verifica os limites antes de expandir expoentes com setScale.
                    if (valor.stripTrailingZeros().scale() > 2)
                        throw new IllegalArgumentException("Valor deve ter no máximo duas casas decimais.");
                    if ((long) valor.precision() - valor.scale() > 13)
                        throw new IllegalArgumentException("Valor excede a capacidade de 13 dígitos inteiros.");
                    valor = valor.setScale(2, java.math.RoundingMode.UNNECESSARY);

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
    public Pagina<ImportacaoResumo> listarImportacoes(int page, int size, TipoImportacao tipo, StatusImportacao status) {
        Long empresaId = empresaAtual.id();
        Specification<Importacao> filtro = (root, query, cb) -> {
            var predicados = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicados.add(cb.equal(root.get("empresa").get("id"), empresaId));
            if (tipo != null) predicados.add(cb.equal(root.get("tipo"), tipo));
            if (status != null) predicados.add(cb.equal(root.get("status"), status));
            return cb.and(predicados.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return Pagina.de(importacaoRepository.findAll(filtro, Pagina.pedido(page, size))
                .map(importacao -> new ImportacaoResumo(
                        importacao.getId(),
                        importacao.getNomeArquivo(),
                        importacao.getTipo(),
                        importacao.getStatus(),
                        lancamentoRepository.countByImportacao_IdAndImportacao_Empresa_Id(importacao.getId(), empresaId),
                        erroImportacaoRepository.countByImportacao_IdAndImportacao_Empresa_Id(importacao.getId(), empresaId)
                )));
    }

    @Transactional(readOnly = true)
    public List<ErroImportacaoResumo> listarErros(Long importacaoId) {
        importacaoRepository.findByIdAndEmpresa_Id(importacaoId, empresaAtual.id())
                .orElseThrow(NaoEncontradoException::new);

        return erroImportacaoRepository
                .findAllByImportacao_IdAndImportacao_Empresa_IdOrderByNumeroLinhaAsc(importacaoId, empresaAtual.id())
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

    private String validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new IllegalArgumentException("O arquivo está vazio.");
        if (arquivo.getSize() > 5 * 1024 * 1024) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Limite de arquivo: 5 MiB.");
        String mime = arquivo.getContentType();
        if (mime != null && !Set.of("text/csv", "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream")
                .contains(mime.split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Envie um arquivo CSV de texto UTF-8.");
        }
        try (var stream = arquivo.getInputStream()) {
            byte[] bytes = stream.readNBytes(5 * 1024 * 1024 + 1);
            if (bytes.length > 5 * 1024 * 1024) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Limite de arquivo: 5 MiB.");
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if (texto.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
                throw new IllegalArgumentException("CSV contém caracteres de controle inválidos.");
            return texto.startsWith("\uFEFF") ? texto.substring(1) : texto;
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Não foi possível ler o CSV em UTF-8."); }
    }
}
