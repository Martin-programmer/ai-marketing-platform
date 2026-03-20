package com.amp.campaigns;

import java.math.BigDecimal;

public record AiCampaignProposalRequest(
        String brief,
        String budgetType,
        BigDecimal dailyBudget
) {
}