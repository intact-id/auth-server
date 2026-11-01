package com.intact_id.api.kyc;

import com.intact_id.dto.request.kyc.ClaimHandoffRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.kyc.KycDraftResponseDto;
import com.intact_id.dto.response.kyc.KycDraftSessionResponseDto;
import com.intact_id.dto.response.kyc.KycHandoffResponseDto;
import com.intact_id.security.authentication.WidgetSessionContext;
import com.intact_id.service.kyc.handoff.KycHandoffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.intact_id.api.kyc.WidgetResponses.respond;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC Phone Handoff", description = "Continue a widget verification on a phone via QR code or link")
public class KycHandoffController {

    private final KycHandoffService handoffService;

    @PostMapping("/widget/drafts/{draftId}/handoff")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Create a handoff link", description = "Render the url as a QR code. Replaces any earlier code for this draft.")
    public ResponseEntity<ApiResponse<KycHandoffResponseDto>> createHandoff(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @PathVariable String draftId) {
        return respond(HttpStatus.CREATED, "Handoff created", handoffService.createHandoff(context, draftId));
    }

    @DeleteMapping("/widget/drafts/{draftId}/handoff")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Take the verification back from the phone", description = "Revokes the code and ends the phone's session.")
    public ResponseEntity<ApiResponse<KycDraftResponseDto>> revokeHandoff(
            @RequestAttribute(WidgetSessionContext.ATTRIBUTE) WidgetSessionContext context,
            @PathVariable String draftId) {
        return respond(HttpStatus.OK, "Handoff revoked", handoffService.revokeHandoff(context, draftId));
    }

    @PostMapping("/handoff/claim")
    @SecurityRequirements
    @Operation(summary = "Claim a handoff code (phone)", description = "Public. Exchanges the one-time code from the QR fragment for a draft token.")
    public ResponseEntity<ApiResponse<KycDraftSessionResponseDto>> claim(
            @Valid @RequestBody ClaimHandoffRequestDto request,
            HttpServletRequest httpRequest) {
        // Not X-Forwarded-For directly: clients can rotate it. RemoteIpValve resolves it from trusted proxies only.
        KycDraftSessionResponseDto session = handoffService.claim(
                request.getCode(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return respond(HttpStatus.OK, "Handoff claimed", session);
    }
}
