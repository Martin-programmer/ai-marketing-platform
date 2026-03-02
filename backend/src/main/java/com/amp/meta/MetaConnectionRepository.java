package com.amp.meta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetaConnectionRepository extends JpaRepository<MetaConnection, UUID> {

    Optional<MetaConnection> findByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<MetaConnection> findByIdAndAgencyId(UUID id, UUID agencyId);
}
