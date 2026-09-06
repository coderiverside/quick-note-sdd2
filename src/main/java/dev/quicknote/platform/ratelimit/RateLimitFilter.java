package dev.quicknote.platform.ratelimit;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import io.vertx.core.http.HttpServerRequest;

import dev.quicknote.security.CurrentUserProvider;
import dev.quicknote.shared.correlation.CorrelationContext;
import dev.quicknote.shared.problem.ErrorCode;
import dev.quicknote.shared.problem.ProblemDetail;
import dev.quicknote.shared.problem.ProblemMappers;

/**
 * Applies the rate limit, just after authentication so the principal is known.
 *
 * <p>Exceeding either limit answers {@code 429} with {@code Retry-After}, as a problem document like
 * every other refusal.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class RateLimitFilter implements ContainerRequestFilter {

    @Inject
    RateLimiter limiter;

    @Inject
    CurrentUserProvider currentUser;

    @Context
    HttpServerRequest httpRequest;

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath().replaceAll("^/", "");
        if (path.startsWith("q/health")) {
            return; // probes must answer even when a caller is being throttled
        }

        RateLimiter.Decision decision = limiter.check(currentUser.subject(), sourceIp(request));
        if (decision.allowed()) {
            return;
        }
        request.abortWith(Response.status(ErrorCode.RATE_LIMITED.status())
                .type(ProblemMappers.PROBLEM_JSON)
                .header("Retry-After", decision.retryAfterSeconds())
                .header("RateLimit-Remaining", 0)
                .entity(ProblemDetail.of(
                        ErrorCode.RATE_LIMITED, "Too many requests.", path, CorrelationContext.current()))
                .build());
    }

    /**
     * Trusts {@code X-Forwarded-For} only for its first entry, and only as a key: a spoofed value can
     * cost the spoofer their own bucket, never anyone else's access.
     */
    private String sourceIp(ContainerRequestContext request) {
        String forwarded = request.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return httpRequest == null || httpRequest.remoteAddress() == null
                ? "unknown"
                : httpRequest.remoteAddress().hostAddress();
    }
}
