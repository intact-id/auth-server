package com.intact_id.orchestration.web;

import com.intact_id.dto.response.ApiResponse;
import com.intact_id.orchestration.verification.ManualReviewService;
import com.intact_id.orchestration.verification.ManualReviewService.QueueFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "List verifications pending manual review",
            description = "Longest-waiting first. Each item carries its review record (reason, assignee).")
    public ResponseEntity<ApiResponse<Page<VerificationStatusResponseDto>>> getPendingReviewQueue(
            @Parameter(description = "all (default), mine or unassigned") @RequestParam(defaultValue = "all") String assignee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        QueueFilter filter = switch (assignee.trim().toLowerCase()) {
            case "all" -> QueueFilter.ALL;
            case "mine", "me" -> QueueFilter.MINE;
            case "unassigned" -> QueueFilter.UNASSIGNED;
            default -> throw new com.intact_id.util.exception.ApplicationExceptions.IntactBadRequestException(
                    "assignee must be all, mine or unassigned");
        };
        Page<VerificationStatusResponseDto> queue = manualReviewService.getPendingReviewQueue(
                filter, authentication.getName(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));

        return ok("Review queue retrieved", queue);
    }

    @PostMapping("/{verificationId}/review")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Approve or reject a verification pending manual review",
            description = "Records the reviewer, time and notes. The automated risk score and failure reason are kept.")
    public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> resolveReview(
            @PathVariable String verificationId,
            @RequestParam String decision,
            @RequestParam(required = false) String notes,
            Authentication authentication) {

        return ok("Review recorded", manualReviewService.resolveReview(
                verificationId, decision, notes, authentication.getName()));
    }

    @PostMapping("/{verificationId}/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Assign a pending review to yourself", description = "Takes the case over if someone else holds it.")
    public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> assignToMe(
            @PathVariable String verificationId,
            Authentication authentication) {

        return ok("Review assigned", manualReviewService.assign(verificationId, authentication.getName()));
    }

    @DeleteMapping("/{verificationId}/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Return a pending review to the unassigned queue")
    public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> unassign(
            @PathVariable String verificationId,
            Authentication authentication) {

        return ok("Review unassigned", manualReviewService.unassign(verificationId, authentication.getName()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).responseCode(200).responseMessage(message).data(data).build());
    }
}
