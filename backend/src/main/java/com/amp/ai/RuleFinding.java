package com.amp.ai;

import java.util.UUID;

/**
 * Raw finding from the Rule Engine before guardrails filtering.
 */
public record RuleFinding(
    String suggestionType,    // DIAGNOSTIC, BUDGET_ADJUST, PAUSE, CREATIVE_TEST, COPY_REFRESH
    String riskLevel,         // LOW, MEDIUM, HIGH
    String scopeType,         // CAMPAIGN, ADSET, AD
    UUID scopeId,             // entity ID
    String rawRationale,      // Brief description of what was found
    String payloadJson,       // JSON payload for the suggestion
    double confidence,        // 0.0 - 1.0
    double changePercent,     // For BUDGET_ADJUST: the % change
    int dataDays,             // How many days of data were analyzed
    long totalConversions,    // Total conversions in the analysis window
    boolean requiresMinData   // Whether this rule type requires minimum data
) {}
