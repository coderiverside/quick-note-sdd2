package dev.quicknote.shared.correlation;

import java.util.UUID;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Accepts an inbound {@code traceparent} or {@code X-Request-Id}, generates one when absent, puts it
 * in the MDC so every log line carries it, and echoes it on the response.
 *
 * <p>Without this, "what failed, for whom, and since when" cannot be answered from logs alone.
 */
@Provider
public class CorrelationFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request) {
        String correlationId = fromTraceparent(request.getHeaderString(CorrelationContext.TRACEPARENT));
        if (correlationId == null) {
            correlationId = trimToNull(request.getHeaderString(CorrelationContext.HEADER));
        }
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        CorrelationContext.put(correlationId);
        request.setProperty(CorrelationContext.MDC_KEY, correlationId);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object correlationId = request.getProperty(CorrelationContext.MDC_KEY);
        if (correlationId != null) {
            response.getHeaders().putSingle(CorrelationContext.HEADER, correlationId);
        }
        CorrelationContext.clear();
    }

    /** W3C traceparent is {@code version-traceid-spanid-flags}; the trace id is the useful part. */
    private static String fromTraceparent(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        return parts.length >= 3 && parts[1].length() == 32 ? parts[1] : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
