package com.intact_id.service.kyc.draft;

import com.intact_id.db.model.kyc.KycDraftDocument;
import com.intact_id.service.storage.DocumentStorage;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Exposes a stored draft document as a MultipartFile so it can go through the regular submit path.
 */
class StoredDocumentMultipartFile implements MultipartFile {

    private final DocumentStorage storage;
    private final KycDraftDocument document;

    StoredDocumentMultipartFile(DocumentStorage storage, KycDraftDocument document) {
        this.storage = storage;
        this.document = document;
    }

    @Override
    public String getName() {
        return document.getDocumentType();
    }

    @Override
    public String getOriginalFilename() {
        return document.getOriginalFilename();
    }

    @Override
    public String getContentType() {
        return document.getMimeType();
    }

    @Override
    public boolean isEmpty() {
        return document.getFileSize() == null || document.getFileSize() == 0;
    }

    @Override
    public long getSize() {
        return document.getFileSize() != null ? document.getFileSize() : 0;
    }

    @Override
    public byte[] getBytes() throws IOException {
        try (InputStream in = getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Override
    public InputStream getInputStream() {
        return storage.get(document.getStorageKey());
    }

    @Override
    public void transferTo(File dest) throws IOException {
        try (InputStream in = getInputStream()) {
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
