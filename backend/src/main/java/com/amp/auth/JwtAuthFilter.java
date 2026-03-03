package com.amp.auth;

import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Extracts a Bearer JWT from the {@code Authorization} header, validates it,
 * and populates both the Spring Security {@code SecurityContext} and the
 * {@link TenantContextHolder}.
 * <p>
 * Skips auth endpoints ({@code /api/v1/auth/**}) and actuator so they can
 * be accessed without a token.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthFilter(JwtService jwtService, UserAccountRepository userAccountRepository) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs")
                || path.startsWith("/webjars");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTH_HEADER);
        log.debug("JwtAuthFilter processing {} {} | Auth header present: {}",
                request.getMethod(), request.getRequestURI(), authHeader != null);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);

                // Only accept access tokens (not refresh)
                String type = claims.get("type", String.class);
                if (!"access".equals(type)) {
                    log.warn("Non-access token used for API call");
                    filterChain.doFilter(request, response);
                    return;
                }

                UUID userId = UUID.fromString(claims.getSubject());
                String role = claims.get("role", String.class);
                String agencyId = claims.get("agencyId", String.class);

                UserAccount user = userAccountRepository.findById(userId).orElse(null);
                if (user != null && "ACTIVE".equals(user.getStatus())) {
                    // Set request attributes (for backward compatibility)
                    request.setAttribute("currentUser", user);
                    request.setAttribute("currentUserId", userId);
                    request.setAttribute("currentUserRole", role);
                    request.setAttribute("currentUserEmail", user.getEmail());
                    if (agencyId != null && !agencyId.isBlank()) {
                        request.setAttribute("currentAgencyId", UUID.fromString(agencyId));
                    }

                    // SET SPRING SECURITY CONTEXT
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT auth successful for user: {} role: {}", user.getEmail(), role);

                    // Client context
                    String clientIdStr = claims.get("clientId", String.class);
                    UUID clientId = (clientIdStr != null && !clientIdStr.isBlank())
                            ? UUID.fromString(clientIdStr) : user.getClientId();
                    if (clientId != null) {
                        request.setAttribute("currentClientId", clientId);
                    }

                    // Tenant context
                    UUID agencyUuid = agencyId != null && !agencyId.isBlank() ? UUID.fromString(agencyId) : user.getAgencyId();
                    TenantContextHolder.set(new TenantContext(agencyUuid, userId, user.getEmail(), role, clientId));
                } else {
                    log.debug("JWT user not found or inactive: {}", userId);
                }

            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Invalid JWT token: {}", e.getMessage());
                // Let the request through unauthenticated — Spring Security will 401
            }
        } else {
            log.debug("No Bearer token in request to {}", request.getRequestURI());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
