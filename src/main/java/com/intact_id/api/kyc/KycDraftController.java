package com.intact_id.api.kyc;

import com.intact_id.dto.request.kyc.DraftDetailsRequestDto;
import com.intact_id.dto.request.kyc.ResumeDraftRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.KycVerificationResponseDto;
import com.intact_id.dto.response.kyc.KycDraftResponseDto;
import com.intact_id.dto.response.kyc.KycDraftSessionResponseDto;
import com.intact_id.security.authentication.WidgetSessionContext;
import com.intact_id.service.kyc.draft.KycDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static com.intact_id.api.kyc.WidgetResponses.respond;

@RestController
@RequestMapping("/api/v1/kyc/widget/drafts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMPANY')")
@Tag(name = "KYC Widget Drafts", description = "Resumable, multi-step widget verifications. Authenticated via X-Session-Token.")
public class KycDraftController {

    private final KycDraftService draftService;

    @PostMapping
    @Operation(summary = "Start a draft", description = "Returns a draft token for subsequent calls and a resume token to keep in localStorage.")
    public ResponseEntity<ApiResponse<KycDraftSessionResponseDto>> createDraft(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context) {
        return respond(HttpStatus.CREATED, "Draft created", draftService.createDraft(context));
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume a draft", description = "Reattach to an unfinished draft using the resume token from localStorage. 404 means start a new draft.")
    public ResponseEntity<ApiResponse<KycDraftSessionResponseDto>> resumeDraft(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @Valid @RequestBody ResumeDraftRequestDto request) {
        return respond(HttpStatus.OK, "Draft resumed", draftService.resumeDraft(context, request.getResumeToken()));
    }

    @PostMapping("/token")
    @Operation(summary = "Renew the draft token")
    public ResponseEntity<ApiResponse<KycDraftSessionResponseDto>> refreshToken(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context) {
        return respond(HttpStatus.OK, "Token renewed", draftService.refreshToken(context));
    }

    @GetMapping("/{draftId}")
    @Operation(summary = "Get draft progress", description = "Masked details, uploaded document types and handoff status. Poll this from the desktop during a handoff.")
    public ResponseEntity<ApiResponse<KycDraftResponseDto>> getDraft(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @PathVariable String draftId) {
        return respond(HttpStatus.OK, "OK", draftService.getDraft(context, draftId));
    }

    @PutMapping("/{draftId}/details")
    @Operation(summary = "Save applicant details", description = "Partial update; omitted fields are kept.")
    public ResponseEntity<ApiResponse<KycDraftResponseDto>> updateDetails(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @PathVariable String draftId,
            @Valid @RequestBody DraftDetailsRequestDto request) {
        return respond(HttpStatus.OK, "Details saved", draftService.updateDetails(context, draftId, request));
    }

    @PostMapping(value = "/{draftId}/documents/{documentType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace a document", description = "documentType: selfie, id_front, id_back, proof_of_address")
    public ResponseEntity<ApiResponse<KycDraftResponseDto>> uploadDocument(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @PathVariable String draftId,
            @PathVariable String documentType,
            @RequestPart("file") MultipartFile file) {
        return respond(HttpStatus.OK, "Document uploaded",
                draftService.uploadDocument(context, draftId, documentType, file));
    }

    @DeleteMapping("/{draftId}/documents/{documentType}")
    @Operation(summary = "Remove a document")
    public ResponseEntity<ApiResponse<KycDraftResponseDto>> deleteDocument(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @PathVariable String draftId,
            @PathVariable String documentType) {
        return respond(HttpStatus.OK, "Document removed", draftService.deleteDocument(context, draftId, documentType));
    }

    @PostMapping("/{draftId}/submit")
    @Operation(summary = "Submit the draft for verification", description = "Then poll GET /api/v1/kyc/widget/verify/{verificationId}.")
    public ResponseEntity<ApiResponse<KycVerificationResponseDto>> submitDraft(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @PathVariable String draftId) {
        return respond(HttpStatus.CREATED, "Verification submitted", draftService.submitDraft(context, draftId));
    }

    @DeleteMapping("/{draftId}")
    @Operation(summary = "Discard the draft", description = "Start over: deletes the draft, its details and uploaded files.")
    public ResponseEntity<ApiResponse<Void>> cancelDraft(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @PathVariable String draftId) {
        draftService.cancelDraft(context, draftId);
        return respond(HttpStatus.OK, "Draft discarded", null);
    }
}
