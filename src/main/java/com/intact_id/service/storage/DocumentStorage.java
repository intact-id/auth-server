package com.intact_id.service.storage;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Blob storage for uploaded KYC documents. Keys are '/'-separated, e.g. drafts/{draftId}/selfie.
 */
public interface DocumentStorage {

    void put(String key, InputStream content, long size, String contentType);

    InputStream get(String key);

    /** Size and content type of a stored object; throws IntactResourceNotFoundException if missing. */
    StoredObject head(String key);

    /** The first bytes of an object, for checking its file signature without downloading it. */
    byte[] readStart(String key, int length);

    void copy(String sourceKey, String targetKey);

    void delete(String key);

    void deletePrefix(String prefix);

    /** True when browsers can upload straight to the store with a presigned URL. */
    default boolean supportsDirectUpload() {
        return false;
    }

    default PresignedUpload presignUpload(String key, String contentType, long size, Duration ttl) {
        throw new UnsupportedOperationException("Direct upload is not supported by this storage");
    }

    record StoredObject(long size, String contentType) {
    }

    /** headers must be sent with the PUT exactly as given, or the signature check fails. */
    record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
    }
}
