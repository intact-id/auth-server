package com.intact_id.orchestration.web;

import com.intact_id.orchestration.VerificationDocument;
import com.intact_id.orchestration.model.VerificationDocumentRepository;
import com.intact_id.orchestration.verification.VerificationDocumentFiles;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
public class DocumentController {

    private final VerificationDocumentRepository documentRepository;
    private final VerificationDocumentFiles verificationDocumentFiles;

    /**
     * Get document image by verification ID and document ID
     */
    @GetMapping("/verifications/{verificationId}/documents/{documentId}")
    public ResponseEntity<Resource> getDocument(
            @PathVariable String verificationId,
            @PathVariable String documentId) {

        log.info("Fetching document: {} for verification: {}", documentId, verificationId);

        VerificationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Document not found: " + documentId));

        if (!document.getVerificationRequest().getId().equals(verificationId)) {
            throw new ApplicationExceptions.IntactUnauthorizedException(
                    "Document does not belong to this verification");
        }

        MediaType contentType;
        try {
            contentType = document.getMimeType() != null
                    ? MediaType.parseMediaType(document.getMimeType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IllegalArgumentException e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String filename = document.getOriginalFilename() != null
                ? document.getOriginalFilename()
                : document.getDocumentType();

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(new InputStreamResource(verificationDocumentFiles.open(document)));
    }
}
