package com.intact_id.config.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intact_id.dto.response.ApiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static com.intact_id.config.web.DownstreamApiResponses.respond;

/**
 * Errors from controllers that front billing-service or sactions-service: the service's own 4xx message is passed
 * on; it being down or failing becomes 502, without its internals.
 */
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = DownstreamApi.class)
public class DownstreamApiExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<JsonNode>> handleDownstream(FeignException e, HandlerMethod handler) {
        String service = serviceName(handler);
        if (e.status() >= 400 && e.status() < 500) {
            JsonNode body = parse(e.contentUTF8());
            String message = body != null && body.hasNonNull("message") ? body.get("message").asText()
                    : service + " rejected the request";
            JsonNode errors = body != null && body.hasNonNull("errors") ? body.get("errors") : null;
            return respond(HttpStatus.valueOf(e.status()), message, errors);
        }
        log.error("{} call failed: {}", service, e.getMessage());
        return respond(HttpStatus.BAD_GATEWAY, service + " is unavailable, try again shortly", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, message, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return respond(HttpStatus.BAD_REQUEST, e instanceof MissingServletRequestParameterException m
                ? m.getMessage() : "Malformed request", null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(RuntimeException e) {
        return respond(HttpStatus.FORBIDDEN, "Access denied", null);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException e, HandlerMethod handler) {
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (status != null) return respond(status.code(), e.getMessage(), null);
        log.error("Unhandled error in {} endpoint", serviceName(handler), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return objectMapper.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String serviceName(HandlerMethod handler) {
        DownstreamApi api = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), DownstreamApi.class);
        return api != null ? api.value() : "Service";
    }
}
