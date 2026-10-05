package com.smartpark.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filter that establishes and manages request correlation IDs for end-to-end tracing.
 *
 * Workflow:
 * 1. Inspects the incoming 'X-Correlation-ID' HTTP header.
 * 2. Validates format against regex (alphanumeric, underscores, hyphens, max 64 chars) to guard against header injection or unbounded size.
 * 3. Generates a standard UUID if absent or invalid.
 * 4. Places the correlation ID in the SLF4J MDC under 'correlationId' for structured logging.
 * 5. Echoes the correlation ID in the 'X-Correlation-ID' response header.
 * 6. Always clears the MDC entry in a finally block to prevent thread-pool leakage.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank() || !VALID_CORRELATION_ID.matcher(correlationId.trim()).matches()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = correlationId.trim();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
