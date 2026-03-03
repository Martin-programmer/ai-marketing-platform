package com.amp.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditLog}.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.agencyId = :agencyId
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:clientId IS NULL OR a.clientId = :clientId)
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findFiltered(
            @Param("agencyId") UUID agencyId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("entityId") UUID entityId,
            @Param("clientId") UUID clientId,
            Pageable pageable);
}
