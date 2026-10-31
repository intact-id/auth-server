package com.intact_id.service.storage;

import com.intact_id.util.exception.ApplicationExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "kyc.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorage implements DocumentStorage {

    private final Path baseDir;

    public LocalDocumentStorage(
            @Value("${kyc.storage.local.base-dir:${app.documents.base-dir:${user.dir}/uploads}}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        Path target = resolve(key);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store document {}", key, e);
            throw new ApplicationExceptions.IntactInternalServerException("Failed to store document");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("Document not found");
        } catch (IOException e) {
            log.error("Failed to read document {}", key, e);
            throw new ApplicationExceptions.IntactInternalServerException("Failed to read document");
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Failed to delete document {}", key, e);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        Path dir = resolve(prefix);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete documents under {}", prefix, e);
        }
    }

    private Path resolve(String key) {
        Path path = baseDir.resolve(key).normalize();
        if (!path.startsWith(baseDir) || path.equals(baseDir)) {
            throw new ApplicationExceptions.IntactBadRequestException("Invalid document key");
        }
        return path;
    }
}
