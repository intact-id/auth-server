package com.intact_id.config.web;

import com.intact_id.dto.response.ApiResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

public final class DownstreamApiResponses {

    private DownstreamApiResponses() {
    }

    public static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatusCode status, String message, T data) {
        return ResponseEntity.status(status)
                .body(ApiResponse.<T>builder()
                        .responseCode(status.value())
                        .responseMessage(message)
                        .success(status.is2xxSuccessful())
                        .data(data)
                        .build());
    }
}
