package com.amp.agency;

import com.amp.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service handling agency CRUD and lifecycle operations.
 */
@Service
@Transactional
public class AgencyService {

    private final AgencyRepository agencyRepository;

    public AgencyService(AgencyRepository agencyRepository) {
        this.agencyRepository = agencyRepository;
    }

    @Transactional(readOnly = true)
    public List<AgencyResponse> listAgencies() {
        return agencyRepository.findAll()
                .stream().map(AgencyResponse::from).toList();
    }

    public AgencyResponse createAgency(CreateAgencyRequest req) {
        Agency a = new Agency();
        a.setName(req.name());
        a.setStatus("ACTIVE");
        a.setPlanCode(req.planCode());
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());

        Agency saved = agencyRepository.save(a);
        return AgencyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AgencyResponse getAgency(UUID agencyId) {
        Agency a = findOrThrow(agencyId);
        return AgencyResponse.from(a);
    }

    public AgencyResponse updateAgency(UUID agencyId, UpdateAgencyRequest req) {
        Agency a = findOrThrow(agencyId);

        if (req.name() != null && !req.name().isBlank()) {
            a.setName(req.name());
        }
        if (req.planCode() != null && !req.planCode().isBlank()) {
            a.setPlanCode(req.planCode());
        }

        Agency saved = agencyRepository.save(a);
        return AgencyResponse.from(saved);
    }

    public AgencyResponse suspendAgency(UUID agencyId) {
        Agency a = findOrThrow(agencyId);
        a.setStatus("SUSPENDED");
        Agency saved = agencyRepository.save(a);
        return AgencyResponse.from(saved);
    }

    public AgencyResponse reactivateAgency(UUID agencyId) {
        Agency a = findOrThrow(agencyId);
        a.setStatus("ACTIVE");
        Agency saved = agencyRepository.save(a);
        return AgencyResponse.from(saved);
    }

    private Agency findOrThrow(UUID agencyId) {
        return agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agency", agencyId));
    }
}
