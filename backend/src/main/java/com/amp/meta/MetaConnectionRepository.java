package com.amp.meta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetaConnectionRepository extends JpaRepository<MetaConnection, UUID> {

    Optional<MetaConnection> findByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<MetaConnection> findByClientId(UUID clientId);

    Optional<MetaConnection> findByIdAndAgencyId(UUID id, UUID agencyId);

    /** Find all connections by status (e.g. "CONNECTED"). */
    List<MetaConnection> findByStatus(String status);

        List<MetaConnection> findByStatusIn(List<String> statuses);

        @Query("""
                        select c from MetaConnection c
                        where c.status in :statuses
                            and c.tokenExpiresAt is not null
                            and c.tokenExpiresAt <= :before
                        """)
        List<MetaConnection> findExpiringConnections(@Param("statuses") List<String> statuses,
                                                                                                 @Param("before") OffsetDateTime before);
}
