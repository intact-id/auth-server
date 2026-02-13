package com.intact_id.api.kyc;

import com.intact_id.db.model.kyc.VerificationDocument;
import com.intact_id.db.repository.kyc.VerificationDocumentRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
public class DocumentController {

    private final VerificationDocumentRepository documentRepository;

    @Value("${app.documents.base-dir:${user.dir}/uploads}")
    private String documentsBaseDir;

    /**
     * Get document image by verification ID and document ID
     */
    @GetMapping("/verifications/{verificationId}/documents/{documentId}")
    public ResponseEntity<Resource> getDocument(
            @PathVariable String verificationId,
            @PathVariable String documentId) {

        log.info("Fetching document: {} for verification: {}", documentId, verificationId);

        // Find document
        VerificationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Document not found: " + documentId));

        // Verify document belongs to the verification
        if (!document.getVerificationRequest().getId().equals(verificationId)) {
            throw new ApplicationExceptions.IntactUnauthorizedException(
                    "Document does not belong to this verification");
        }

        try {
            // Load from absolute path if present, otherwise resolve from configured base directory.
            Path filePath = resolveDocumentPath(document.getFilePath());

            File file = filePath.toFile();

            log.info("Attempting to serve file: {}", filePath.toAbsolutePath());

            if (!file.exists()) {
                log.error("Document file not found at: {}", filePath.toAbsolutePath());
                log.error("Stored path in DB: {}", document.getFilePath());
                log.error("Working directory: {}", System.getProperty("user.dir"));
                throw new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Document file not found");
            }

            Resource resource = new FileSystemResource(file);

            // Determine content type
            String contentType = document.getMimeType();
            if (contentType == null) {
                contentType = Files.probeContentType(filePath);
            }
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            log.info("Serving document: {} with content type: {}", document.getOriginalFilename(), contentType);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + document.getOriginalFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Error serving document: {}", documentId, e);
            throw new ApplicationExceptions.IntactBadRequestException(
                    "Failed to serve document: " + e.getMessage());
        }
    }

    private Path resolveDocumentPath(String storedPath) {
        Path rawPath = Paths.get(storedPath);
        if (rawPath.isAbsolute()) {
            return rawPath.normalize();
        }

        Path basePath = Paths.get(documentsBaseDir).toAbsolutePath().normalize();
        Path resolved = basePath.resolve(rawPath).normalize();
        if (Files.exists(resolved)) {
            return resolved;
        }

        // Backward compatibility: older records may be relative to process working directory.
        return Paths.get(System.getProperty("user.dir"), storedPath).toAbsolutePath().normalize();
    }
}
