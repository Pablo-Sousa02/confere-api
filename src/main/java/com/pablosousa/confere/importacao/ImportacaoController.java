package com.pablosousa.confere.importacao;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}