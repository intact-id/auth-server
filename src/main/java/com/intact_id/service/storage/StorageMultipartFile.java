package com.intact_id.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Exposes a stored document as a MultipartFile, so stored files go through the same code as uploads.
 * Each read streams from storage again.
 */
public class StorageMultipartFile implements MultipartFile {

    private final DocumentStorage storage;
    private final String storageKey;
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final long size;

    public StorageMultipartFile(DocumentStorage storage, String storageKey, String name,
                                String originalFilename, String contentType, Long size) {
        this.storage = storage;
        this.storageKey = storageKey;
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size != null ? size : 0;
    }

    public String getStorageKey() {
        return storageKey;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public byte[] getBytes() throws IOException {
        try (InputStream in = getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Override
    public InputStream getInputStream() {
        return storage.get(storageKey);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        try (InputStream in = getInputStream()) {
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
