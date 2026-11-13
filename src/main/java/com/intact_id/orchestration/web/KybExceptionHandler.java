package com.intact_id.orchestration.web;

import com.intact_id.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.intact_id.orchestration.web.WidgetResponses.respond;

/** KYB errors ("Workflow x needs: …", "Unknown director or owner") in the usual ApiResponse body. */
@Slf4j
@RestControllerAdvice(assignableTypes = KybController.class)
public class KybExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return respond(HttpStatus.FORBIDDEN, "Access denied", null);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException e) {
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (responseStatus != null) {
            return respond(responseStatus.code(), e.getMessage(), null);
        }
        log.error("Unhandled error in KYB endpoint", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null);
    }
}
