package com.amp.creatives;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for interacting with S3 storage for creative assets.
 * Handles presigned URL generation, existence checks, and deletions.
 * Falls back to local stubs when S3 is disabled.
 *
 * <p>NOTE: S3 bucket must have CORS configured to allow PUT from the frontend domain.
 * <pre>
 * aws s3api put-bucket-cors --bucket amp-creatives-staging --cors-configuration '{
 *   "CORSRules": [{
 *     "AllowedHeaders": ["*"],
 *     "AllowedMethods": ["PUT", "GET"],
 *     "AllowedOrigins": ["https://adverion.xyz", "http://localhost:5173"],
 *     "ExposeHeaders": ["ETag"],
 *     "MaxAgeSeconds": 3600
 *   }]
 * }'
 * </pre>
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Properties props;
    private S3Client s3Client;
    private S3Presigner presigner;

    public S3StorageService(S3Properties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        if (!props.isEnabled()) {
            log.info("S3 storage disabled — using local fallback");
            return;
        }
        try {
            Region region = Region.of(props.getRegion());
            this.s3Client = S3Client.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            this.presigner = S3Presigner.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            log.info("S3 client initialized for bucket: {} in {}", props.getBucket(), props.getRegion());
        } catch (Exception e) {
            log.warn("S3 client initialization failed, S3 uploads will not work: {}", e.getMessage());
            props.setEnabled(false);
        }
    }

    @PreDestroy
    public void destroy() {
        if (s3Client != null) s3Client.close();
        if (presigner != null) presigner.close();
    }

    /**
     * Generate an S3 key for a creative asset.
     * Format: agencies/{agencyId}/clients/{clientId}/creatives/{assetId}/{filename}
     */
    public String generateS3Key(UUID agencyId, UUID clientId, UUID assetId, String filename) {
        return String.format("agencies/%s/clients/%s/creatives/%s/%s",
                agencyId, clientId, assetId, sanitizeFilename(filename));
    }

    /**
     * Generate a presigned PUT URL for uploading a file to S3.
     */
    public String generatePresignedPutUrl(String s3Key, String contentType, long contentLength) {
        if (!props.isEnabled()) {
            return "http://localhost:8080/api/v1/creatives/local-upload/" + s3Key;
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(s3Key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(props.getPresignedUrlExpirationMinutes()))
                .putObjectRequest(putRequest)
                .build();

        String url = presigner.presignPutObject(presignRequest).url().toString();
        log.info("Generated presigned PUT URL for key: {}", s3Key);
        return url;
    }

    /**
     * Generate a presigned GET URL for downloading/viewing a file from S3.
     */
    public String generatePresignedGetUrl(String s3Key) {
        if (!props.isEnabled()) {
            return "http://localhost:8080/api/v1/creatives/local-download/" + s3Key;
        }

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(props.getBucket())
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(props.getPresignedUrlExpirationMinutes()))
                .getObjectRequest(getRequest)
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Check if an object exists in S3.
     */
    public boolean objectExists(String s3Key) {
        if (!props.isEnabled()) return true; // assume exists in local mode

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * Get object metadata (size, content type).
     */
    public HeadObjectResponse getObjectMetadata(String s3Key) {
        return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(props.getBucket())
                .key(s3Key)
                .build());
    }

    /**
     * Download file from S3 as byte array.
     * Returns null when S3 is disabled (local dev) or on error.
     */
    public byte[] downloadFile(String s3Key) {
        if (!props.isEnabled()) {
            log.info("S3 disabled — cannot download file: {}", s3Key);
            return null;
        }
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(s3Key)
                    .build();
            ResponseBytes<software.amazon.awssdk.services.s3.model.GetObjectResponse> response =
                    s3Client.getObjectAsBytes(request);
            byte[] bytes = response.asByteArray();
            log.info("Downloaded S3 object: {} ({}KB)", s3Key, bytes.length / 1024);
            return bytes;
        } catch (Exception e) {
            log.error("Failed to download S3 object {}: {}", s3Key, e.getMessage());
            return null;
        }
    }

    /**
     * Delete an object from S3.
     */
    public void deleteObject(String s3Key) {
        if (!props.isEnabled()) return;

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.getBucket())
                .key(s3Key)
                .build());
        log.info("Deleted S3 object: {}", s3Key);
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
