package com.pablosousa.confere.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<ApiError> naoEncontrado(HttpServletRequest request) {
        return criarResposta(HttpStatus.NOT_FOUND, "Recurso não encontrado.", request);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiError> autenticacao(HttpServletRequest request) {
        return criarResposta(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.", request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> acesso(HttpServletRequest request) {
        return criarResposta(HttpStatus.FORBIDDEN, "Acesso não permitido.", request);
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> status(org.springframework.web.server.ResponseStatusException e, HttpServletRequest request) {
        if (e.getStatusCode().value() == 429) {
            var resposta = criarResposta(HttpStatus.TOO_MANY_REQUESTS, e.getReason(), request);
            return ResponseEntity.status(429).header("Retry-After", "60").body(resposta.getBody());
        }
        return criarResposta(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason(), request);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacao(org.springframework.web.bind.MethodArgumentNotValidException e, HttpServletRequest request) {
        String mensagem = e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage())
                .distinct().sorted().collect(java.util.stream.Collectors.joining("; "));
        return criarResposta(HttpStatus.BAD_REQUEST, mensagem, request);
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> corpoInvalido(HttpServletRequest request) {
        return criarResposta(HttpStatus.BAD_REQUEST, "Requisição inválida. Confira campos, tipos e parâmetros obrigatórios.", request);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflito(HttpServletRequest request) {
        return criarResposta(HttpStatus.CONFLICT, "Dados conflitantes. Verifique se o e-mail já está em uso.", request);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tamanho(HttpServletRequest request) {
        return criarResposta(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede o limite de 5 MiB ou requisição excede 6 MiB.", request);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> metodo(HttpServletRequest request) {
        return criarResposta(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP não permitido.", request);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> media(HttpServletRequest request) {
        return criarResposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado.", request);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> rota(HttpServletRequest request) { return naoEncontrado(request); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inesperado(HttpServletRequest request) {
        return criarResposta(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação.", request);
    }

    public static void escreverErro(jakarta.servlet.http.HttpServletResponse response, HttpServletRequest request,
                                    int status, String mensagem) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        var erro = new ApiError(Instant.now(), status, HttpStatus.valueOf(status).getReasonPhrase(), mensagem, request.getRequestURI());
        tools.jackson.databind.json.JsonMapper.builder().build().writeValue(response.getWriter(), erro);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> tratarRegraInvalida(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return criarResposta(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> tratarArquivoAusente(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return criarResposta(
                HttpStatus.BAD_REQUEST,
                "O campo '" + exception.getRequestPartName() + "' é obrigatório.",
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> tratarParametroAusente(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return criarResposta(
                HttpStatus.BAD_REQUEST,
                "O parâmetro '" + exception.getParameterName() + "' é obrigatório.",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tratarTipoInvalido(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return criarResposta(
                HttpStatus.BAD_REQUEST,
                "O valor informado para '" + exception.getName() + "' é inválido.",
                request
        );
    }

    private ResponseEntity<ApiError> criarResposta(
            HttpStatus status,
            String mensagem,
            HttpServletRequest request
    ) {
        ApiError erro = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(erro);
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path
    ) {
    }
}
