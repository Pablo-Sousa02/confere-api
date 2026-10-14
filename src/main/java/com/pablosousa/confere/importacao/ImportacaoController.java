package com.pablosousa.confere.importacao;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/importacoes")
public class ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.Operation(summary="Importa CSV da empresa autenticada",
            description="Campo multipart arquivo; tipo VENDAS ou RECEBIMENTOS. UTF-8, separador ;, cabeçalhos referencia;data;descricao;valor. Até 5 MiB e 20000 registros. Datas ISO e valores positivos com ponto/vírgula; erros de linha ficam disponíveis para consulta.")
    public ImportacaoResultado importar(
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestParam("tipo") TipoImportacao tipo
    ) {
        return importacaoService.importar(arquivo, tipo);
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary="Lista importações da empresa", description="Página com filtros opcionais tipo/status; page começa em zero, size entre 1 e 100.")
    public com.pablosousa.confere.shared.Pagina<ImportacaoResumo> listarImportacoes(
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) TipoImportacao tipo, @RequestParam(required=false) StatusImportacao status) {
        return importacaoService.listarImportacoes(page, size, tipo, status);
    }

    @GetMapping("/{id}/erros")
    @io.swagger.v3.oas.annotations.Operation(summary="Consulta erros de linha de uma importação", description="ID inexistente ou pertencente a outra empresa retorna 404.")
    public List<ErroImportacaoResumo> listarErros(@PathVariable Long id) {
        return importacaoService.listarErros(id);
    }
}
