package com.amp.ops;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC = "correlationId";
    public static final String AGENCY_ID_MDC = "agencyId";
    public static final String USER_ID_MDC = "userId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Extract or generate correlation ID
        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Put into MDC for logging
        MDC.put(CORRELATION_ID_MDC, correlationId);

        // Also extract agency/user from dev headers for MDC logging context
        String agencyId = httpRequest.getHeader("X-Agency-Id");
        String userEmail = httpRequest.getHeader("X-Dev-User-Email");
        if (agencyId != null) MDC.put(AGENCY_ID_MDC, agencyId);
        if (userEmail != null) MDC.put(USER_ID_MDC, userEmail);

        // Return correlation ID in response header
        httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
