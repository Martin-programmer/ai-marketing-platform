package com.amp.clients;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for saving client onboarding questionnaire answers.
 */
public record ClientQuestionnaireRequest(
        // Basic Info
        @NotBlank(message = "contactName is required")
        String contactName,
        @NotBlank(message = "brandName is required")
        String brandName,
        String website,

        // Products & Services
        @NotBlank(message = "productsDescription is required")
        String productsDescription,
        String bestSellers,
        String averageOrderValue,
        String profitMargin,
        String shippingInfo,

        // Target Audience
        @NotBlank(message = "audiences is required")
        String audiences,
        String customerProblem,
        String customerObjections,

        // Brand & Positioning
        @NotBlank(message = "usp is required")
        String usp,
        String competitors,
        String tone,

        // Advertising & Marketing
        @NotBlank(message = "targetLocations is required")
        String targetLocations,
        String adBudgetInfo,
        @NotBlank(message = "marketingGoal is required")
        String marketingGoal,
        String previousAdExperience,
        String previousResults,
        String currentChallenges,

        // Materials & Infrastructure
        String hasCreatives,
        String hasTracking
) {}
