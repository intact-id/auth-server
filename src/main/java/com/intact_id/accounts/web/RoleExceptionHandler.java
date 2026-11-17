package com.intact_id.accounts.web;

import com.intact_id.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Role errors ("Those roles allow things you can't do yourself", "The company needs at least one Admin") as ApiResponse. */
@Slf4j
@RestControllerAdvice(assignableTypes = RoleController.class)
public class RoleExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return respond(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException e) {
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (status != null) {
            return respond(status.code(), e.getMessage());
        }
        log.error("Unhandled error in a roles endpoint", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static ResponseEntity<ApiResponse<Void>> respond(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.<Void>builder()
                .responseCode(status.value()).responseMessage(message).success(false).build());
    }
}
