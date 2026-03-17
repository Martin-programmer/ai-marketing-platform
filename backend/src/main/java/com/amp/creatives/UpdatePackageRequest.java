package com.amp.creatives;

import jakarta.validation.constraints.NotBlank;

public record UpdatePackageRequest(
        @NotBlank(message = "name is required")
        String name,
        String objective,
        String notes
) {
}