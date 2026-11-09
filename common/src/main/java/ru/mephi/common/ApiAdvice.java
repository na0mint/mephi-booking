package ru.mephi.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class ApiAdvice {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorDto> api(ApiException e, HttpServletRequest r) {
        return response(e.status(), e.code(), e.getMessage(), r, Map.of());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorDto> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        Map<String, String> fields = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return response(400, "VALIDATION_ERROR", "Request validation failed", r, fields);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ErrorDto>
    malformed(Exception e, HttpServletRequest r) {
        return response(400, "BAD_REQUEST", "Invalid request parameters or JSON", r, Map.of());
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorDto> denied(Exception e, HttpServletRequest r) {
        return response(403, "FORBIDDEN", "Insufficient permissions", r, Map.of());
    }
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class,
            org.springframework.dao.OptimisticLockingFailureException.class})
    ResponseEntity<ErrorDto>
    conflict(Exception e, HttpServletRequest r) {
        return response(409, "CONFLICT", "Resource conflict; retry with the same requestId", r, Map.of());
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorDto> unexpected(Exception e, HttpServletRequest r) {
        if (e instanceof org.springframework.web.ErrorResponse error && error.getStatusCode().value() < 500) {
            int status = error.getStatusCode().value();
            String code = switch (status) {
                case 404 -> "NOT_FOUND";
                case 405 -> "METHOD_NOT_ALLOWED";
                case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                default -> "BAD_REQUEST";
            };
            return response(status, code,
                    org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase(), r, Map.of());
        }
        LoggerFactory.getLogger(ApiAdvice.class)
                .error("event=unexpected_error traceId={}", MDC.get("traceId"), e);
        return response(500, "INTERNAL_ERROR", "Unexpected server error", r, Map.of());
    }
    private ResponseEntity<ErrorDto> response(
            int status, String code, String message, HttpServletRequest r, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ErrorDto(
                Instant.now(), status, code, message, r.getRequestURI(), MDC.get("traceId"), fields));
    }
}
