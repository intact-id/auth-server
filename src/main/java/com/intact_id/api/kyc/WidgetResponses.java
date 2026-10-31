package com.intact_id.api.kyc;

import com.intact_id.dto.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class WidgetResponses {

    private WidgetResponses() {
    }

    // no-store: responses carry draft tokens and masked PII
    static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.<T>builder()
                        .responseCode(status.value())
                        .responseMessage(message)
                        .success(status.is2xxSuccessful())
                        .data(data)
                        .build());
    }
}
