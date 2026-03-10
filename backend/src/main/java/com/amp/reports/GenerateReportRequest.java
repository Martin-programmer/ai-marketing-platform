package com.amp.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for generating a new report.
 */
public record GenerateReportRequest(
        UUID clientId,
        @NotBlank(message = "reportType is required")
        String reportType,
        @NotNull(message = "periodStart is required")
        LocalDate periodStart,
        @NotNull(message = "periodEnd is required")
        LocalDate periodEnd
) {
}
