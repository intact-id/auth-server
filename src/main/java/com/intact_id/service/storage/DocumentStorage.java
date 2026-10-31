package com.intact_id.service.storage;

import java.io.InputStream;

/**
 * Blob storage for uploaded KYC documents. Keys are '/'-separated, e.g. drafts/{draftId}/selfie.
 */
public interface DocumentStorage {

    void put(String key, InputStream content, long size, String contentType);

    InputStream get(String key);

    void delete(String key);

    void deletePrefix(String prefix);
}
