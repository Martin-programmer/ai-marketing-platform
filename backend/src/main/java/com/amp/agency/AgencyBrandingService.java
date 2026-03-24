package com.amp.agency;

import com.amp.common.exception.ResourceNotFoundException;
import com.amp.config.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service for managing agency branding with a 5-minute in-memory cache.
 */
@Service
@Transactional
public class AgencyBrandingService {

    private static final Logger log = LoggerFactory.getLogger(AgencyBrandingService.class);
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
        private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{3,30}$");
        private static final Set<String> RESERVED_SLUGS = Set.of(
            "login", "admin", "owner", "portal", "api", "auth", "privacy", "terms", "public"
        );

    private final AgencyRepository agencyRepository;
    private final SystemSettingService systemSettingService;

    private final ConcurrentHashMap<UUID, CachedBranding> cache = new ConcurrentHashMap<>();

    public AgencyBrandingService(AgencyRepository agencyRepository,
                                 SystemSettingService systemSettingService) {
        this.agencyRepository = agencyRepository;
        this.systemSettingService = systemSettingService;
    }

    // ── AGENCY_ADMIN endpoints ──────────────────────────────

    @Transactional(readOnly = true)
    public AgencyBrandingResponse getAgencyBranding(UUID agencyId) {
        Agency a = findOrThrow(agencyId);
        return AgencyBrandingResponse.from(a);
    }

    public AgencyBrandingResponse updateBranding(UUID agencyId, UpdateBrandingRequest req) {
        Agency a = findOrThrow(agencyId);

        if (req.slug() != null) {
            String normalizedSlug = normalizeSlug(req.slug());
            if (normalizedSlug == null) {
                a.setSlug(null);
            } else {
                validateSlug(normalizedSlug);
                if (agencyRepository.existsBySlugIgnoreCaseAndIdNot(normalizedSlug, agencyId)) {
                    throw new IllegalStateException("Slug is already taken. Please choose another one.");
                }
                a.setSlug(normalizedSlug);
            }
        }

        if (req.primaryColor() != null) {
            validateColor(req.primaryColor());
            a.setPrimaryColor(req.primaryColor());
        }
        if (req.secondaryColor() != null) {
            validateColor(req.secondaryColor());
            a.setSecondaryColor(req.secondaryColor());
        }
        if (req.accentColor() != null) {
            validateColor(req.accentColor());
            a.setAccentColor(req.accentColor());
        }
        if (req.fontFamily() != null) a.setFontFamily(req.fontFamily());
        if (req.companyEmail() != null) a.setCompanyEmail(req.companyEmail());
        if (req.companyPhone() != null) a.setCompanyPhone(req.companyPhone());
        if (req.companyWebsite() != null) a.setCompanyWebsite(req.companyWebsite());
        if (req.companyAddress() != null) a.setCompanyAddress(req.companyAddress());
        if (req.emailFooterText() != null) a.setEmailFooterText(req.emailFooterText());
        if (req.reportFooterText() != null) a.setReportFooterText(req.reportFooterText());
        if (req.reportDisclaimer() != null) a.setReportDisclaimer(req.reportDisclaimer());
        if (req.portalWelcomeMessage() != null) a.setPortalWelcomeMessage(req.portalWelcomeMessage());
        if (req.customCss() != null) a.setCustomCss(req.customCss());

        Agency saved = agencyRepository.save(a);
        evictCache(agencyId);

        log.info("Updated branding for agency {}", agencyId);
        return AgencyBrandingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public SlugAvailabilityResponse checkSlugAvailability(UUID agencyId, String slug) {
        String normalizedSlug = normalizeSlug(slug);
        if (normalizedSlug == null) {
            return new SlugAvailabilityResponse(false, nextAvailableSlug("agency", agencyId));
        }

        if (!isValidSlug(normalizedSlug)) {
            return new SlugAvailabilityResponse(false, nextAvailableSlug(toSlugCandidate(normalizedSlug), agencyId));
        }

        boolean taken = agencyRepository.existsBySlugIgnoreCaseAndIdNot(normalizedSlug, agencyId);
        if (!taken) {
            return new SlugAvailabilityResponse(true, null);
        }

        return new SlugAvailabilityResponse(false, nextAvailableSlug(normalizedSlug, agencyId));
    }

    /**
     * Save logo information on the agency record after a successful S3 upload.
     */
    public void saveLogo(UUID agencyId, String s3Key, String logoUrl) {
        Agency a = findOrThrow(agencyId);
        a.setLogoS3Key(s3Key);
        a.setLogoUrl(logoUrl);
        agencyRepository.save(a);
        evictCache(agencyId);
        log.info("Saved logo for agency {}: s3Key={}", agencyId, s3Key);
    }

    // ── Internal branding info (for email / reports) ────────

    /**
     * Returns cached branding info for use in emails and reports.
     */
    @Transactional(readOnly = true)
    public AgencyBrandingInfo getAgencyBrandingInfo(UUID agencyId) {
        CachedBranding cached = cache.get(agencyId);
        if (cached != null && !cached.isExpired()) {
            return cached.info;
        }

        Agency a = findOrThrow(agencyId);
        AgencyBrandingInfo info = AgencyBrandingInfo.from(a);
        cache.put(agencyId, new CachedBranding(info));
        return info;
    }

    // ── Public branding ─────────────────────────────────────

    /**
     * Returns limited branding for a specific agency (used on login page).
     */
    @Transactional(readOnly = true)
    public PublicBrandingResponse getPublicAgencyBranding(UUID agencyId) {
        Agency a = agencyRepository.findById(agencyId).orElse(null);
        if (a == null) {
            return getPlatformBranding(); // fallback
        }
        return new PublicBrandingResponse(
                a.getName(),
                a.getLogoUrl(),
                a.getPrimaryColor() != null ? a.getPrimaryColor() : "#1976D2",
                a.getSecondaryColor() != null ? a.getSecondaryColor() : "#424242",
                null // agencies don't have a tagline
        );
    }

    @Transactional(readOnly = true)
    public PublicBrandingResponse getPublicAgencyBrandingBySlug(String slug) {
        String normalizedSlug = normalizeSlug(slug);
        if (normalizedSlug == null) {
            throw new ResourceNotFoundException("Agency branding slug", slug);
        }

        Agency a = agencyRepository.findBySlugIgnoreCase(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Agency branding slug", normalizedSlug));

        return new PublicBrandingResponse(
                a.getName(),
                a.getLogoUrl(),
                a.getPrimaryColor() != null ? a.getPrimaryColor() : "#1976D2",
                a.getSecondaryColor() != null ? a.getSecondaryColor() : "#424242",
                null
        );
    }

    @Transactional(readOnly = true)
    public String getAgencySlug(UUID agencyId) {
        return agencyRepository.findById(agencyId)
                .map(Agency::getSlug)
                .map(this::normalizeSlug)
                .orElse(null);
    }

    /**
     * Returns Adverion platform branding from system_setting.
     */
    public PublicBrandingResponse getPlatformBranding() {
        return new PublicBrandingResponse(
                systemSettingService.getString("branding.platform.name", "Adverion"),
                systemSettingService.getString("branding.platform.logo.url", "/adverion-logo.png"),
                systemSettingService.getString("branding.platform.primary.color", "#1976D2"),
                systemSettingService.getString("branding.platform.secondary.color", "#424242"),
                systemSettingService.getString("branding.platform.tagline", "AI-Powered Marketing Platform")
        );
    }

    // ── Portal branding ─────────────────────────────────────

    /**
     * Returns branding for CLIENT_USER portal view.
     */
    @Transactional(readOnly = true)
    public PortalBrandingResponse getPortalBranding(UUID agencyId) {
        Agency a = findOrThrow(agencyId);
        String poweredByText = systemSettingService.getString("branding.powered.by.text", "Powered by Adverion");
        boolean poweredByVisible = systemSettingService.getBoolean("branding.powered.by.visible", true);

        return new PortalBrandingResponse(
                a.getName(),
                a.getLogoUrl(),
                a.getPrimaryColor() != null ? a.getPrimaryColor() : "#1976D2",
                a.getSecondaryColor() != null ? a.getSecondaryColor() : "#424242",
                a.getAccentColor() != null ? a.getAccentColor() : "#FF9800",
                a.getFontFamily() != null ? a.getFontFamily() : "Roboto, sans-serif",
                a.getPortalWelcomeMessage(),
                poweredByText,
                poweredByVisible
        );
    }

    // ── Helpers ──────────────────────────────────────────────

    public void evictCache(UUID agencyId) {
        cache.remove(agencyId);
    }

    private void validateColor(String color) {
        if (!HEX_COLOR.matcher(color).matches()) {
            throw new IllegalArgumentException(
                    "Invalid hex color '" + color + "'. Must match #RRGGBB format.");
        }
    }

    private void validateSlug(String slug) {
        if (!isValidSlug(slug)) {
            throw new IllegalArgumentException(
                    "Invalid slug. Use lowercase letters, numbers, and hyphens only (3-30 characters), and avoid reserved words.");
        }
    }

    private boolean isValidSlug(String slug) {
        return slug != null
                && SLUG_PATTERN.matcher(slug).matches()
                && !slug.startsWith("-")
                && !slug.endsWith("-")
                && !slug.contains("--")
                && !RESERVED_SLUGS.contains(slug);
    }

    private String normalizeSlug(String slug) {
        if (slug == null) {
            return null;
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String toSlugCandidate(String value) {
        String normalized = normalizeSlug(value);
        if (normalized == null) {
            return "agency";
        }
        String candidate = normalized.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (candidate.isBlank()) {
            return "agency";
        }
        if (candidate.length() > 30) {
            candidate = candidate.substring(0, 30).replaceAll("-+$", "");
        }
        if (candidate.length() < 3) {
            candidate = (candidate + "-agency").replaceAll("^-|-$", "");
        }
        if (RESERVED_SLUGS.contains(candidate)) {
            candidate = abbreviateSlug(candidate, "-agency");
        }
        return candidate;
    }

    private String nextAvailableSlug(String base, UUID agencyId) {
        String candidate = toSlugCandidate(base);

        if (!agencyRepository.existsBySlugIgnoreCaseAndIdNot(candidate, agencyId)) {
            return candidate;
        }

        String digitalCandidate = abbreviateSlug(candidate, "-digital");
        if (!agencyRepository.existsBySlugIgnoreCaseAndIdNot(digitalCandidate, agencyId)) {
            return digitalCandidate;
        }

        for (int i = 2; i < 1000; i++) {
            String numbered = abbreviateSlug(candidate, "-" + i);
            if (!agencyRepository.existsBySlugIgnoreCaseAndIdNot(numbered, agencyId)) {
                return numbered;
            }
        }

        return abbreviateSlug(candidate, "-brand");
    }

    private String abbreviateSlug(String base, String suffix) {
        int maxBaseLength = 30 - suffix.length();
        String trimmed = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
        trimmed = trimmed.replaceAll("-+$", "");
        if (trimmed.length() < 3) {
            trimmed = "agency";
        }
        return trimmed + suffix;
    }

    private Agency findOrThrow(UUID agencyId) {
        return agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agency", agencyId));
    }

    private record CachedBranding(AgencyBrandingInfo info, long timestamp) {
        CachedBranding(AgencyBrandingInfo info) { this(info, System.currentTimeMillis()); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }

    public record SlugAvailabilityResponse(boolean available, String suggestion) {}
}
