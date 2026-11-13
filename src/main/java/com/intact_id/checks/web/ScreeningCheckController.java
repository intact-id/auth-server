package com.intact_id.checks.web;

import com.intact_id.checks.RiskScreening;
import com.intact_id.config.web.DownstreamApi;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Screens a name on demand, outside any verification. Intact staff only: the match runs on the hosted
 * screening service and is not billed to a company. Nothing is stored; the request is in the audit log.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/screening")
@DownstreamApi("Screening service")
@Tag(name = "Screening", description = "Sanctions and PEP screening results")
public class ScreeningCheckController {

    private final RiskScreening riskScreening;

    /** type PERSON needs firstName and lastName (birthDate optional); BUSINESS needs name (jurisdiction optional). */
    public record CheckRequest(String type, String firstName, String lastName, String birthDate, String name, String jurisdiction) {
    }

    public record CheckResult(String verdict, int hits, double topScore, boolean pep, boolean adverseMedia,
                              List<RiskScreening.Match> matches) {
    }

    @PostMapping("/check")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Screen a person or business now", description = "Against sanctions, PEP and watch lists. Not stored.")
    public ResponseEntity<ApiResponse<CheckResult>> check(@RequestBody CheckRequest request, Authentication authentication) {
        RiskScreening.Subject subject = subjectOf(request);
        RiskScreening.Screening screening = riskScreening.screen(subject);
        log.info("On-demand screening by {}: {} → {} ({} hits)", authentication.getName(),
                subject.business() ? "business" : "person", screening.verdict(), screening.hits());
        CheckResult result = new CheckResult(screening.verdict().name(), screening.hits(), screening.topScore(),
                screening.pep(), screening.adverseMedia(), screening.matches());
        return ResponseEntity.ok(ApiResponse.<CheckResult>builder()
                .success(true).responseCode(HttpStatus.OK.value()).responseMessage("Screening complete").data(result).build());
    }

    private static RiskScreening.Subject subjectOf(CheckRequest request) {
        if ("BUSINESS".equalsIgnoreCase(request.type())) {
            if (blank(request.name())) throw new ApplicationExceptions.IntactBadRequestException("Enter the business name");
            return RiskScreening.Subject.business(null, request.name().trim(), trimOrNull(request.jurisdiction()));
        }
        if (blank(request.firstName()) || blank(request.lastName())) {
            throw new ApplicationExceptions.IntactBadRequestException("Enter a first and last name");
        }
        return RiskScreening.Subject.person(null, request.firstName().trim(), request.lastName().trim(), trimOrNull(request.birthDate()));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimOrNull(String value) {
        return blank(value) ? null : value.trim();
    }
}
