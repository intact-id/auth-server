package com.intact_id.workflow.web;

import com.intact_id.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class WorkflowResponses {

    private WorkflowResponses() {
    }

    static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status)
                .body(ApiResponse.<T>builder()
                        .responseCode(status.value())
                        .responseMessage(message)
                        .success(status.is2xxSuccessful())
                        .data(data)
                        .build());
    }
}
