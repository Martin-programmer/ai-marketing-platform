package com.amp.creatives;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO to initiate a creative asset upload.
 * Backend creates the asset record (UPLOADING) and returns a presigned PUT URL.
 */
public record UploadInitRequest(
        @NotBlank(message = "fileName is required")
        String fileName,
        @NotBlank(message = "mimeType is required")
        String mimeType,
        @Positive(message = "sizeBytes must be positive")
        long sizeBytes,
        String assetType,       // IMAGE, VIDEO, DOC — optional, auto-detected from mimeType
        String checksumSha256   // optional at init, can be provided at complete
) {}
