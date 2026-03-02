package com.amp.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<Report> findByIdAndAgencyId(UUID id, UUID agencyId);
}
