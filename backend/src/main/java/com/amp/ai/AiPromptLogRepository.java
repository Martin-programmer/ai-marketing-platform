package com.amp.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.UUID;

public interface AiPromptLogRepository extends JpaRepository<AiPromptLog, UUID> {

    @Query("""
        SELECT COALESCE(SUM(p.costUsd), 0)
        FROM AiPromptLog p
        WHERE p.agencyId = :agencyId
          AND EXTRACT(MONTH FROM p.createdAt) = EXTRACT(MONTH FROM CURRENT_TIMESTAMP)
          AND EXTRACT(YEAR FROM p.createdAt) = EXTRACT(YEAR FROM CURRENT_TIMESTAMP)
        """)
    BigDecimal getMonthlySpendByAgency(UUID agencyId);

    @Query("""
        SELECT COALESCE(SUM(p.costUsd), 0)
        FROM AiPromptLog p
        WHERE CAST(p.createdAt AS DATE) = CURRENT_DATE
        """)
    BigDecimal getTodayTotalSpend();
}
