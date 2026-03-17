package com.amp.ai;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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

    /* ---- Owner AI Audit queries ---- */

    @Query("""
        SELECT p FROM AiPromptLog p
        WHERE p.createdAt >= :from AND p.createdAt < :to
          AND (:agencyId IS NULL OR p.agencyId = :agencyId)
          AND (:module IS NULL OR p.module = :module)
          AND (:success IS NULL OR p.success = :success)
        ORDER BY p.createdAt DESC
        """)
    Page<AiPromptLog> findAuditLogs(@Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to,
                                     @Param("agencyId") UUID agencyId,
                                     @Param("module") String module,
                                     @Param("success") Boolean success,
                                     Pageable pageable);

    /* Summary aggregates */
    @Query("""
        SELECT COUNT(p), SUM(CASE WHEN p.success = true THEN 1 ELSE 0 END),
               SUM(CASE WHEN p.success = false THEN 1 ELSE 0 END),
               COALESCE(SUM(p.totalTokens), 0), COALESCE(SUM(p.promptTokens), 0),
               COALESCE(SUM(p.completionTokens), 0), COALESCE(SUM(p.costUsd), 0),
               COALESCE(AVG(p.durationMs), 0)
        FROM AiPromptLog p
        WHERE p.createdAt >= :from AND p.createdAt < :to
          AND (:agencyId IS NULL OR p.agencyId = :agencyId)
        """)
    List<Object[]> summaryStats(@Param("from") OffsetDateTime from,
                  @Param("to") OffsetDateTime to,
                  @Param("agencyId") UUID agencyId);

    @Query("""
        SELECT p.module, COUNT(p), COALESCE(SUM(p.totalTokens), 0), COALESCE(SUM(p.costUsd), 0)
        FROM AiPromptLog p
        WHERE p.createdAt >= :from AND p.createdAt < :to
          AND (:agencyId IS NULL OR p.agencyId = :agencyId)
        GROUP BY p.module ORDER BY COUNT(p) DESC
        """)
    List<Object[]> summaryByModule(@Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to,
                                    @Param("agencyId") UUID agencyId);

    @Query("""
        SELECT p.agencyId, COUNT(p), COALESCE(SUM(p.totalTokens), 0), COALESCE(SUM(p.costUsd), 0)
        FROM AiPromptLog p
        WHERE p.createdAt >= :from AND p.createdAt < :to
          AND (:agencyId IS NULL OR p.agencyId = :agencyId)
        GROUP BY p.agencyId ORDER BY SUM(p.costUsd) DESC
        """)
    List<Object[]> summaryByAgency(@Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to,
                                    @Param("agencyId") UUID agencyId);

    @Query("""
        SELECT CAST(p.createdAt AS DATE), COUNT(p), COALESCE(SUM(p.totalTokens), 0), COALESCE(SUM(p.costUsd), 0)
        FROM AiPromptLog p
        WHERE p.createdAt >= :from AND p.createdAt < :to
          AND (:agencyId IS NULL OR p.agencyId = :agencyId)
        GROUP BY CAST(p.createdAt AS DATE)
        ORDER BY CAST(p.createdAt AS DATE) DESC
        """)
    List<Object[]> summaryByDay(@Param("from") OffsetDateTime from,
                                 @Param("to") OffsetDateTime to,
                                 @Param("agencyId") UUID agencyId);

    @Query("""
        SELECT p.clientId, COUNT(p), COALESCE(SUM(p.totalTokens), 0), COALESCE(SUM(p.costUsd), 0)
        FROM AiPromptLog p
        WHERE p.createdAt >= :from AND p.createdAt < :to
          AND p.agencyId = :agencyId
        GROUP BY p.clientId ORDER BY SUM(p.costUsd) DESC
        """)
    List<Object[]> summaryByClient(@Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to,
                                    @Param("agencyId") UUID agencyId);
}
