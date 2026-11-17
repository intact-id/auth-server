package com.intact_id.orchestration.web;

import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.orchestration.companydocs.CompanyDocuments;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

/** A company's compliance documents: the company uploads them, Intact staff review them. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company-documents")
@Tag(name = "Company documents", description = "Compliance documents a company keeps with Intact ID")
public class CompanyDocumentController {

    private final CompanyDocuments documents;

    @GetMapping
    @PreAuthorize("hasAuthority('documents:view')")
    @Operation(summary = "Every required document with its latest version, history and readiness")
    public ResponseEntity<ApiResponse<CompanyDocuments.Overview>> overview(@RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "OK", documents.overview(SecurityContextUtil.resolveCompanyId(companyId)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('documents:manage')")
    @Operation(summary = "Upload a document (PDF, JPG or PNG, up to 10 MB); a new upload of the same type is the next version")
    public ResponseEntity<ApiResponse<CompanyDocuments.Version>> upload(
            @RequestParam(required = false) String companyId,
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
            @RequestPart("file") MultipartFile file) {
        return respond(HttpStatus.CREATED, "Document uploaded",
                documents.upload(SecurityContextUtil.resolveCompanyId(companyId), type, file, expiresOn));
    }

    @GetMapping("/{id}/file")
    @PreAuthorize("hasAuthority('documents:view')")
    @Operation(summary = "Download a version's file")
    public ResponseEntity<InputStreamResource> file(@PathVariable String id, @RequestParam(required = false) String companyId) {
        CompanyDocuments.Download d = documents.download(SecurityContextUtil.resolveCompanyId(companyId), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .contentLength(d.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(d.content()));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('documents:review-platform')")
    @Operation(summary = "Approve or reject a version (Intact staff)", description = "Body: { \"decision\": \"APPROVE\" | \"REJECT\", \"note\": \"why\" }")
    public ResponseEntity<ApiResponse<CompanyDocuments.Version>> review(@PathVariable String id, @RequestParam String companyId,
                                                                      @RequestBody Map<String, String> body) {
        String decision = String.valueOf(body.get("decision"));
        if (!decision.equals("APPROVE") && !decision.equals("REJECT")) {
            throw new com.intact_id.util.exception.ApplicationExceptions.IntactBadRequestException("decision must be APPROVE or REJECT");
        }
        return respond(HttpStatus.OK, decision.equals("APPROVE") ? "Document approved" : "Document rejected",
                documents.review(SecurityContextUtil.resolveCompanyId(companyId), id, decision.equals("APPROVE"), body.get("note")));
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .responseCode(status.value()).responseMessage(message).success(true).data(data).build());
    }
}
