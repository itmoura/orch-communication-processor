package com.company.communication.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tratamento global de excecoes da API REST, padronizando codigos HTTP 400, 422 e 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(
            OffsetDateTime timestamp,
            int status,
            String error,
            String message,
            List<String> details,
            String path
    ) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "Dados de entrada invalidos", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        String detail = "Payload JSON malformado ou tipo de campo invalido";
        if (ex.getCause() instanceof InvalidFormatException formatEx && !formatEx.getPath().isEmpty()) {
            detail = "Valor invalido para o campo '" + formatEx.getPath().getLast().getFieldName() + "'";
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "Evento BoletoGerado invalido", List.of(detail), request);
    }

    @ExceptionHandler({GuardrailViolationException.class, CustomerNotFoundException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequestBusiness(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        log.warn("Requisicao rejeitada na validacao/guardrail: path={} motivo={}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(InvalidDecisionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDecision(
            InvalidDecisionException ex,
            HttpServletRequest request
    ) {
        log.error("Falha ao gerar decisao valida apos tentativas de recuperacao: violations={}", ex.getViolations());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex.getViolations(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Erro inesperado ao processar evento de comunicacao: path={}", request.getRequestURI(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno inesperado ao processar a decisao de comunicacao",
                List.of(),
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            List<String> details,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
