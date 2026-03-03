package com.amp.auth;

import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Development-only authentication filter.
 * <p>
 * Reads {@code X-Dev-User-Email} header to identify the caller and
 * {@code X-Dev-User-Role} header to assign a role (defaults to
 * {@code AGENCY_ADMIN} if omitted).
 * <p>
 * Also populates {@link TenantContextHolder} with agency/user context
 * from {@code X-Agency-Id} and {@code X-Dev-User-Id} headers.
 * <p>
 * If the email header is missing the request passes through unauthenticated,
 * letting Spring Security's authorization rules decide the outcome.
 */
public class DevAuthFilter extends OncePerRequestFilter {

    private final UserAccountRepository userAccountRepository;

    public DevAuthFilter(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    private static final String HEADER_EMAIL     = "X-Dev-User-Email";
    private static final String HEADER_ROLE      = "X-Dev-User-Role";
    private static final String HEADER_AGENCY_ID = "X-Agency-Id";
    private static final String HEADER_USER_ID   = "X-Dev-User-Id";
    private static final String DEFAULT_ROLE     = "AGENCY_ADMIN";
    private static final UUID   DEFAULT_USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String email = request.getHeader(HEADER_EMAIL);

            if (email != null && !email.isBlank()) {
                String role = request.getHeader(HEADER_ROLE);
                if (role == null || role.isBlank()) {
                    role = DEFAULT_ROLE;
                }

                // Tenant context
                UUID agencyId = parseUuid(request.getHeader(HEADER_AGENCY_ID));
                UUID userId = parseUuid(request.getHeader(HEADER_USER_ID));
                if (userId == null) {
                    userId = DEFAULT_USER_ID;
                }

                UserAccount user = userAccountRepository.findByEmail(email).orElse(null);
                if (user != null && "ACTIVE".equals(user.getStatus())) {
                    // Set request attributes (for backward compatibility)
                    request.setAttribute("currentUser", user);
                    request.setAttribute("currentUserId", userId);
                    request.setAttribute("currentUserRole", role);
                    request.setAttribute("currentUserEmail", user.getEmail());
                    if (agencyId != null) {
                        request.setAttribute("currentAgencyId", agencyId);
                    }

                    // SET SPRING SECURITY CONTEXT
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    // Fallback for local dev header-based auth
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            email, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }

                TenantContextHolder.set(new TenantContext(agencyId, userId, email, role));
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
