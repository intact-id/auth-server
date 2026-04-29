package com.intact_id.api.admin;

import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.VerificationStatusResponseDto;
import com.intact_id.service.kyc.ManualReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/verifications")
@RequiredArgsConstructor
@Tag(name = "Manual Review", description = "Admin endpoints for reviewing flagged KYC verifications")
public class ManualReviewController {

    private final ManualReviewService manualReviewService;

    @GetMapping("/pending-review")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "List verifications pending manual review")
    public ResponseEntity<ApiResponse<Page<VerificationStatusResponseDto>>> getPendingReviewQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<VerificationStatusResponseDto> queue = manualReviewService.getPendingReviewQueue(
                PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.<Page<VerificationStatusResponseDto>>builder()
                .success(true).responseCode(200).data(queue).build());
    }

    @PostMapping("/{verificationId}/review")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Approve or reject a verification pending manual review")
    public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> resolveReview(
            @PathVariable String verificationId,
            @RequestParam String decision,
            @RequestParam(required = false) String notes,
            Authentication authentication) {

        String reviewerEmail = authentication.getName();
        VerificationStatusResponseDto result = manualReviewService.resolveReview(
                verificationId, decision, notes, reviewerEmail);

        return ResponseEntity.ok(ApiResponse.<VerificationStatusResponseDto>builder()
                .success(true).responseCode(200).data(result).build());
    }
}
