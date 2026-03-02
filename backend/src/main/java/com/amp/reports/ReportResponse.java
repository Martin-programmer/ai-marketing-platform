package com.amp.reports;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a report with a computed status field.
 */
public record ReportResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String reportType,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        String htmlContent,
        String pdfS3Key,
        UUID createdBy,
        UUID approvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime approvedAt,
        OffsetDateTime sentAt
) {
    public static ReportResponse from(Report e) {
        String computedStatus;
        if (e.getSentAt() != null) {
            computedStatus = "SENT";
        } else if ("APPROVED".equals(e.getStatus())) {
            computedStatus = "APPROVED";
        } else if ("IN_REVIEW".equals(e.getStatus())) {
            computedStatus = "IN_REVIEW";
        } else {
            computedStatus = e.getStatus();
        }

        return new ReportResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getReportType(), e.getPeriodStart(), e.getPeriodEnd(),
                computedStatus, e.getHtmlContent(), e.getPdfS3Key(),
                e.getCreatedBy(), e.getApprovedBy(),
                e.getCreatedAt(), e.getApprovedAt(), e.getSentAt()
        );
    }
}
