package com.amp.clients;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Client}.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findAllByAgencyId(UUID agencyId);

    List<Client> findByAgencyIdAndIdIn(UUID agencyId, List<UUID> ids);

    Optional<Client> findByIdAndAgencyId(UUID id, UUID agencyId);
}
