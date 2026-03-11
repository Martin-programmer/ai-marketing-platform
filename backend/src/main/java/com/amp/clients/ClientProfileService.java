package com.amp.clients;

import com.amp.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic for client profile operations.
 */
@Service
public class ClientProfileService {

    private final ClientProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public ClientProfileService(ClientProfileRepository profileRepository,
                                ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ClientProfile getProfile(UUID clientId) {
        return profileRepository.findByClientId(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientProfile", clientId));
    }

    @Transactional
    public ClientProfile upsertProfile(UUID agencyId, UUID clientId,
                                       ClientProfileRequest request) {
        String json = toJson(request.profileJson());

        ClientProfile profile = profileRepository.findByClientId(clientId)
                .orElseGet(() -> {
                    ClientProfile p = new ClientProfile();
                    p.setAgencyId(agencyId);
                    p.setClientId(clientId);
                    OffsetDateTime now = OffsetDateTime.now();
                    p.setCreatedAt(now);
                    p.setUpdatedAt(now);
                    return p;
                });

        profile.setWebsite(request.website());
        profile.setProfileJson(json);

        return profileRepository.save(profile);
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid profile JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * Save questionnaire answers, merging with existing profile data.
     */
    @Transactional
    public ClientProfile saveQuestionnaire(UUID agencyId, UUID clientId,
                                           ClientQuestionnaireRequest request,
                                           boolean markComplete) {
        ClientProfile profile = profileRepository.findByClientId(clientId)
                .orElseGet(() -> {
                    ClientProfile p = new ClientProfile();
                    p.setAgencyId(agencyId);
                    p.setClientId(clientId);
                    OffsetDateTime now = OffsetDateTime.now();
                    p.setCreatedAt(now);
                    p.setUpdatedAt(now);
                    return p;
                });

        // Merge questionnaire answers into existing profile_json
        Map<String, Object> existing = parseJson(profile.getProfileJson());
        Map<String, Object> questionnaire = buildQuestionnaireMap(request);

        // Store questionnaire answers under a dedicated key
        existing.put("questionnaire", questionnaire);

        // Also update top-level fields that other AI modules may use
        mergeIfPresent(existing, "usp", request.usp());
        mergeIfPresent(existing, "tone_of_voice", request.tone());
        mergeIfPresent(existing, "target_audiences", request.audiences());
        mergeIfPresent(existing, "competitors", request.competitors());

        profile.setProfileJson(toJson(existing));

        if (request.website() != null && !request.website().isBlank()) {
            profile.setWebsite(request.website());
        }

        if (markComplete) {
            profile.setQuestionnaireCompleted(true);
            profile.setQuestionnaireCompletedAt(OffsetDateTime.now());
        }

        return profileRepository.save(profile);
    }

    /**
     * Get questionnaire answers from profile.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getQuestionnaire(UUID clientId) {
        ClientProfile profile = profileRepository.findByClientId(clientId)
                .orElse(null);
        if (profile == null) {
            return Map.of();
        }
        Map<String, Object> profileData = parseJson(profile.getProfileJson());

        @SuppressWarnings("unchecked")
        Map<String, Object> questionnaire = profileData.containsKey("questionnaire")
                ? (Map<String, Object>) profileData.get("questionnaire")
                : Map.of();

        // Include completion status
        Map<String, Object> result = new LinkedHashMap<>(questionnaire);
        result.put("questionnaireCompleted",
                profile.getQuestionnaireCompleted() != null && profile.getQuestionnaireCompleted());
        result.put("questionnaireCompletedAt", profile.getQuestionnaireCompletedAt());
        return result;
    }

    private Map<String, Object> buildQuestionnaireMap(ClientQuestionnaireRequest r) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "contactName", r.contactName());
        putIfPresent(map, "brandName", r.brandName());
        putIfPresent(map, "website", r.website());
        putIfPresent(map, "productsDescription", r.productsDescription());
        putIfPresent(map, "bestSellers", r.bestSellers());
        putIfPresent(map, "averageOrderValue", r.averageOrderValue());
        putIfPresent(map, "profitMargin", r.profitMargin());
        putIfPresent(map, "shippingInfo", r.shippingInfo());
        putIfPresent(map, "audiences", r.audiences());
        putIfPresent(map, "customerProblem", r.customerProblem());
        putIfPresent(map, "customerObjections", r.customerObjections());
        putIfPresent(map, "usp", r.usp());
        putIfPresent(map, "competitors", r.competitors());
        putIfPresent(map, "tone", r.tone());
        putIfPresent(map, "targetLocations", r.targetLocations());
        putIfPresent(map, "adBudgetInfo", r.adBudgetInfo());
        putIfPresent(map, "marketingGoal", r.marketingGoal());
        putIfPresent(map, "previousAdExperience", r.previousAdExperience());
        putIfPresent(map, "previousResults", r.previousResults());
        putIfPresent(map, "currentChallenges", r.currentChallenges());
        putIfPresent(map, "hasCreatives", r.hasCreatives());
        putIfPresent(map, "hasTracking", r.hasTracking());
        return map;
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private void mergeIfPresent(Map<String, Object> existing, String key, String value) {
        if (value != null && !value.isBlank() && !existing.containsKey(key)) {
            existing.put(key, value);
        }
    }
}
