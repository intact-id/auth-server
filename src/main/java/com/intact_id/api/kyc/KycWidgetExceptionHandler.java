package com.intact_id.api.kyc;

import com.intact_id.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

import static com.intact_id.api.kyc.WidgetResponses.respond;

/**
 * Error mapping for the draft/handoff controllers only; other controllers keep their own handling for now.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {KycDraftController.class, KycHandoffController.class})
public class KycWidgetExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, message, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body", null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return respond(HttpStatus.BAD_REQUEST, "Required part '" + e.getRequestPartName() + "' is missing", null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return respond(HttpStatus.CONFLICT, "Draft was changed on another device, please retry", null);
    }

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
        log.error("Unhandled error in KYC widget draft/handoff endpoint", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null);
    }
}
