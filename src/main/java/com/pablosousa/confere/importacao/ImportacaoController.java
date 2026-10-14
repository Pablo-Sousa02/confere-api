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
    public ImportacaoResultado importar(
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestParam("tipo") TipoImportacao tipo
    ) {
        return importacaoService.importar(arquivo, tipo);
    }

    @GetMapping
    public List<ImportacaoResumo> listarImportacoes() {
        return importacaoService.listarImportacoes();
    }

    @GetMapping("/{id}/erros")
    public List<ErroImportacaoResumo> listarErros(@PathVariable Long id) {
        return importacaoService.listarErros(id);
    }
}