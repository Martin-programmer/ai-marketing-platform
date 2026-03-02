package com.amp.reports;

/**
 * Request DTO for updating report metadata (e.g. PDF S3 key after upload).
 */
public record UpdateReportRequest(
        String pdfS3Key
) {
}
