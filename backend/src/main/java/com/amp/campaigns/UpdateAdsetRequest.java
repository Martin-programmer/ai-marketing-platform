package com.amp.campaigns;

import java.math.BigDecimal;

public record UpdateAdsetRequest(
        String name,
        BigDecimal dailyBudget,
        String targetingJson,
        String optimizationGoal,
        String conversionEvent,
        String status
) {
}