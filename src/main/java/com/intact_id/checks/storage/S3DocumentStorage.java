package com.intact_id.checks.storage;


import com.intact_id.checks.DocumentStorage;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "kyc.storage", name = "type", havingValue = "s3")
public class S3DocumentStorage implements DocumentStorage, DisposableBean {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3DocumentStorage(S3StorageProperties props) {
        requireSetting(props.getEndpoint(), "endpoint");
        requireSetting(props.getBucket(), "bucket");
        requireSetting(props.getAccessKey(), "access-key");
        requireSetting(props.getSecretKey(), "secret-key");

        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
        URI endpoint = URI.create(props.getEndpoint());
        Region region = Region.of(props.getRegion());
        S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        // Only send checksums when an operation requires them; R2 rejects some of the SDK's defaults
        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
        this.bucket = props.getBucket();
        log.info("KYC documents stored in bucket {} at {}", bucket, endpoint.getHost());
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(size)
                            .build(),
                    RequestBody.fromInputStream(content, size));
        } catch (SdkException e) {
            log.error("Failed to store document {}", key, e);
            throw new ApplicationExceptions.IntactInternalServerException("Failed to store document");
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw readFailure(key, e);
        }
    }

    @Override
    public StoredObject head(String key) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return new StoredObject(response.contentLength(), response.contentType());
        } catch (SdkException e) {
            throw readFailure(key, e);
        }
    }

    @Override
    public byte[] readStart(String key, int length) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range("bytes=0-" + (length - 1))
                .build();
        try (ResponseInputStream<GetObjectResponse> in = client.getObject(request)) {
            return in.readNBytes(length);
        } catch (SdkException e) {
            throw readFailure(key, e);
        } catch (IOException e) {
            log.error("Failed to read document {}", key, e);
            throw new ApplicationExceptions.IntactInternalServerException("Failed to read document");
        }
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        try {
            client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucket)
                    .destinationKey(targetKey)
                    .build());
        } catch (SdkException e) {
            log.error("Failed to copy document {} to {}", sourceKey, targetKey, e);
            throw new ApplicationExceptions.IntactInternalServerException("Failed to store document");
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("Failed to delete document {}", key, e);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        String folder = prefix.endsWith("/") ? prefix : prefix + "/";
        try {
            // One delete per object: drafts hold a handful of files, and batch delete needs checksums R2 may reject
            client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(folder).build())
                    .contents()
                    .stream()
                    .map(S3Object::key)
                    .forEach(this::delete);
        } catch (SdkException e) {
            log.warn("Failed to delete documents under {}", prefix, e);
        }
    }

    @Override
    public boolean supportsDirectUpload() {
        return true;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long size, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(r -> r.signatureDuration(ttl).putObjectRequest(put));

        // Browsers set Host and Content-Length themselves and refuse to send them from script
        Map<String, String> headers = new LinkedHashMap<>();
        presigned.signedHeaders().forEach((name, values) -> {
            if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                headers.put(name, String.join(",", values));
            }
        });
        return new PresignedUpload(presigned.url().toString(), headers, presigned.expiration());
    }

    @Override
    public void destroy() {
        presigner.close();
        client.close();
    }

    private RuntimeException readFailure(String key, SdkException e) {
        if (e instanceof S3Exception s3 && s3.statusCode() == 404) {
            return new ApplicationExceptions.IntactResourceNotFoundException("Document not found");
        }
        log.error("Failed to read document {}", key, e);
        return new ApplicationExceptions.IntactInternalServerException("Failed to read document");
    }

    private static void requireSetting(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("kyc.storage.s3." + name + " must be set when kyc.storage.type=s3");
        }
    }
}
