package com.intact_id.service.kyc;

import com.intact_id.db.model.kyc.VerificationDocument;
import com.intact_id.checks.DocumentStorage;
import com.intact_id.checks.StorageMultipartFile;
import com.intact_id.util.FileMultipartFile;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Reads a verification's files from DocumentStorage, or from local disk for rows saved before storage keys. */
@Component
@RequiredArgsConstructor
public class VerificationDocumentFiles {

    private final DocumentStorage documentStorage;

    @Value("${app.documents.base-dir:${user.dir}/uploads}")
    private String documentsBaseDir;

    public MultipartFile asMultipartFile(VerificationDocument document) {
        if (document.getStorageKey() != null) {
            return new StorageMultipartFile(documentStorage, document.getStorageKey(), document.getDocumentType(),
                    document.getOriginalFilename(), document.getMimeType(), document.getFileSize());
        }
        return new FileMultipartFile(legacyPath(document.getFilePath()).toFile(), document.getDocumentType(),
                document.getOriginalFilename(), document.getMimeType());
    }

    public InputStream open(VerificationDocument document) {
        if (document.getStorageKey() != null) {
            return documentStorage.get(document.getStorageKey());
        }
        try {
            return Files.newInputStream(legacyPath(document.getFilePath()));
        } catch (NoSuchFileException e) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("Document file not found");
        } catch (IOException e) {
            throw new ApplicationExceptions.IntactInternalServerException("Failed to read document");
        }
    }

    private Path legacyPath(String storedPath) {
        Path rawPath = Paths.get(storedPath);
        if (rawPath.isAbsolute()) {
            return rawPath.normalize();
        }
        Path resolved = Paths.get(documentsBaseDir).toAbsolutePath().normalize().resolve(rawPath).normalize();
        if (Files.exists(resolved)) {
            return resolved;
        }
        // Older records may be relative to the process working directory
        return Paths.get(System.getProperty("user.dir"), storedPath).toAbsolutePath().normalize();
    }
}
