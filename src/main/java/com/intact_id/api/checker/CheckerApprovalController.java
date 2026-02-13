package com.intact_id.api.checker;

import com.intact_id.dto.request.ApplicationApprovalDecisionDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.ApplicationApprovalResponseDto;
import com.intact_id.dto.response.CheckerApprovalQueueResponseDto;
import com.intact_id.service.checker.CheckerApprovalService;
import com.intact_id.util.SecurityContextUtil;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 14:35
 */

@Slf4j
@RestController
@RequestMapping("/api/admin/applications")
@RequiredArgsConstructor
@Tag(name = "Admin - Checker Approval", description = "Final approval endpoints")
public class CheckerApprovalController {

    private final CheckerApprovalService checkerApprovalService;

    @GetMapping("/approvals/pending-checker")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get pending maker approvals awaiting checker action")
    public ResponseEntity<ApiResponse<Page<CheckerApprovalQueueResponseDto>>> getPendingCheckerApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var data = checkerApprovalService.getPendingCheckerApprovals(PageRequest.of(page, size));

        return ResponseEntity.ok(
                ApiResponse.<Page<CheckerApprovalQueueResponseDto>>builder()
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("Pending checker approvals fetched successfully")
                        .success(true)
                        .data(data)
                        .build()
        );
    }

    @PostMapping("/approvals/{approvalId}/checker-decision")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Checker final decision (approve/reject)")
    public ResponseEntity<ApiResponse<ApplicationApprovalResponseDto>> checkerDecision(
            @PathVariable String approvalId,
            @Valid @RequestBody ApplicationApprovalDecisionDto request) {

        try {


            log.info("Checker decision for approval: {}", approvalId);
            request.setApprovalId(approvalId);
            request.setApprovedBy(SecurityContextUtil.getAuthenticatedUser().getId());
            var response = checkerApprovalService.performCheckerApproval(request);


            return ResponseEntity.status(HttpStatus.OK).body(
                    ApiResponse.<ApplicationApprovalResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Approved successfully")
                            .success(true)
                            .data(response)
                            .build()
            );
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<ApplicationApprovalResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("Application approval request not found")
                            .success(false)
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build()
            );
        } catch (ApplicationExceptions.InvalidActionException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.<ApplicationApprovalResponseDto>builder()
                            .responseCode(HttpStatus.BAD_REQUEST.value())
                            .responseMessage("Invalid action performed")
                            .success(false)
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build()
            );
        }
    }
}
    
