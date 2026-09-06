package dev.quicknote.shared.problem;

import jakarta.annotation.Priority;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.UnauthorizedException;
import org.jboss.logging.Logger;

import dev.quicknote.shared.correlation.CorrelationContext;

/**
 * Turns every failure into a contract-conformant {@code application/problem+json} document.
 *
 * <p>The rule these mappers exist to hold: nothing internal reaches the client. A 500 carries a code
 * and a correlation id and nothing else; the diagnosis lives in the log line with the same id.
 */
public final class ProblemMappers {

    public static final String PROBLEM_JSON = "application/problem+json";

    private static final Logger LOG = Logger.getLogger(ProblemMappers.class);

    private ProblemMappers() {}

    static Response respond(ProblemDetail problem) {
        return Response.status(problem.status())
                .type(PROBLEM_JSON)
                .entity(problem)
                .build();
    }

    static String instanceOf(UriInfo uriInfo) {
        return uriInfo == null ? null : uriInfo.getPath();
    }

    /** Deliberate failures: the code and message were chosen when the failure was defined. */
    @Provider
    public static class DomainExceptionMapper implements ExceptionMapper<DomainException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(DomainException exception) {
            return respond(ProblemDetail.validation(
                    exception.error(),
                    exception.getMessage(),
                    instanceOf(uriInfo),
                    CorrelationContext.current(),
                    exception.fieldErrors()));
        }
    }

    /**
     * Optimistic-lock loss is a contractual 409, not a 500 — the client can re-read and retry, and
     * telling it so is the difference between a recoverable conflict and an opaque server fault.
     */
    @Provider
    public static class OptimisticLockMapper implements ExceptionMapper<OptimisticLockException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(OptimisticLockException exception) {
            var conflict = new DomainException.NoteVersionConflict();
            return respond(ProblemDetail.of(
                    conflict.error(), conflict.getMessage(), instanceOf(uriInfo), CorrelationContext.current()));
        }
    }

    /**
     * Authentication failures are reported as one indistinguishable outcome: missing, malformed,
     * expired, wrong issuer, wrong audience, and bad signature all look identical from outside.
     */
    @Provider
    public static class AuthenticationMapper implements ExceptionMapper<AuthenticationFailedException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(AuthenticationFailedException exception) {
            return Response.status(ErrorCode.UNAUTHENTICATED.status())
                    .type(PROBLEM_JSON)
                    .header("WWW-Authenticate", "Bearer")
                    .entity(ProblemDetail.of(
                            ErrorCode.UNAUTHENTICATED,
                            "Authentication required.",
                            instanceOf(uriInfo),
                            CorrelationContext.current()))
                    .build();
        }
    }

    @Provider
    public static class UnauthorizedMapper implements ExceptionMapper<UnauthorizedException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(UnauthorizedException exception) {
            return Response.status(ErrorCode.UNAUTHENTICATED.status())
                    .type(PROBLEM_JSON)
                    .header("WWW-Authenticate", "Bearer")
                    .entity(ProblemDetail.of(
                            ErrorCode.UNAUTHENTICATED,
                            "Authentication required.",
                            instanceOf(uriInfo),
                            CorrelationContext.current()))
                    .build();
        }
    }

    /**
     * Boundary validation failures.
     *
     * <p>Quarkus ships its own violation mapper; this one takes precedence so that a failed
     * constraint produces the same contractual shape as every other rejection, naming each offending
     * field rather than emitting a framework-specific report.
     */
    @Provider
    @Priority(1)
    public static class ConstraintViolationMapper
            implements ExceptionMapper<jakarta.validation.ConstraintViolationException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(jakarta.validation.ConstraintViolationException exception) {
            java.util.List<FieldError> errors = exception.getConstraintViolations().stream()
                    .map(v -> new FieldError(leafOf(v.getPropertyPath().toString()), codeOf(v), v.getMessage()))
                    .toList();
            var failure = new DomainException.ValidationFailed(errors);
            return respond(ProblemDetail.validation(
                    failure.error(), failure.getMessage(), instanceOf(uriInfo), CorrelationContext.current(), errors));
        }

        /** "create.request.title" is a framework detail; the client cares about "title". */
        private static String leafOf(String propertyPath) {
            int lastDot = propertyPath.lastIndexOf('.');
            return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
        }

        private static String codeOf(jakarta.validation.ConstraintViolation<?> violation) {
            String annotation = violation
                    .getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType()
                    .getSimpleName();
            return switch (annotation) {
                case "Size" -> FieldError.TOO_LONG;
                case "NotNull" -> FieldError.REQUIRED;
                case "NotBlank", "NotEmpty" -> FieldError.BLANK;
                case "Min", "Max" -> FieldError.OUT_OF_RANGE;
                default -> FieldError.INVALID_FORMAT;
            };
        }
    }

    /**
     * Failures the framework raises before our code sees them — an unreadable body, an unsupported
     * media type, a missing route.
     *
     * <p>Quarkus REST answers some of these itself with its own JSON shape. Rewriting them here keeps
     * one promise true without exception: every error this service returns is a problem document.
     */
    @Provider
    @Priority(2)
    public static class WebApplicationExceptionMapper
            implements ExceptionMapper<jakarta.ws.rs.WebApplicationException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(jakarta.ws.rs.WebApplicationException exception) {
            // WebApplicationException always carries a response, so there is no null case to handle.
            int status = exception.getResponse().getStatus();
            ErrorCode code =
                    switch (status) {
                        case 400, 406, 415 -> ErrorCode.MALFORMED_REQUEST;
                        case 401 -> ErrorCode.UNAUTHENTICATED;
                        case 404 -> ErrorCode.NOTE_NOT_FOUND;
                        case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
                        case 429 -> ErrorCode.RATE_LIMITED;
                        case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
                        default -> ErrorCode.INTERNAL_ERROR;
                    };
            if (code == ErrorCode.INTERNAL_ERROR) {
                LOG.errorf(exception, "Unhandled web failure [correlationId=%s]", CorrelationContext.current());
            }
            return respond(ProblemDetail.of(code, detailFor(code), instanceOf(uriInfo), CorrelationContext.current()));
        }

        private static String detailFor(ErrorCode code) {
            return switch (code) {
                case MALFORMED_REQUEST -> "The request body is not valid JSON for this operation.";
                case UNAUTHENTICATED -> "Authentication required.";
                case NOTE_NOT_FOUND -> "No such note.";
                case PAYLOAD_TOO_LARGE -> "The request body is larger than this service accepts.";
                case RATE_LIMITED -> "Too many requests.";
                default -> "The request could not be completed.";
            };
        }
    }

    /** The last resort. Logs everything, discloses nothing. */
    @Provider
    @Priority(5000)
    public static class UnhandledMapper implements ExceptionMapper<Throwable> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(Throwable exception) {
            String correlationId = CorrelationContext.current();

            // Failures raised while the transaction commits arrive wrapped, after the resource
            // method has already returned. Unwrapping is what keeps a lost optimistic-lock race a
            // contractual 409 the client can retry, instead of an opaque 500.
            DomainException domain = unwrapDomain(exception);
            if (domain != null) {
                return respond(ProblemDetail.validation(
                        domain.error(), domain.getMessage(), instanceOf(uriInfo), correlationId, domain.fieldErrors()));
            }
            if (isUniqueViolation(exception, "ux_labels_owner_name")) {
                // The unique index is what actually makes label naming safe: the application check
                // loses a race between two concurrent creates, and this is where that loss lands.
                var conflict = new DomainException.LabelNameConflict();
                return respond(
                        ProblemDetail.of(conflict.error(), conflict.getMessage(), instanceOf(uriInfo), correlationId));
            }
            if (isOptimisticLock(exception)) {
                var conflict = new DomainException.NoteVersionConflict();
                return respond(
                        ProblemDetail.of(conflict.error(), conflict.getMessage(), instanceOf(uriInfo), correlationId));
            }

            LOG.errorf(exception, "Unhandled failure [correlationId=%s]", correlationId);
            return respond(ProblemDetail.of(
                    ErrorCode.INTERNAL_ERROR,
                    "The request could not be completed.",
                    instanceOf(uriInfo),
                    correlationId));
        }

        /**
         * How deep to walk a cause chain. Bounded rather than cycle-checked: a chain can be circular
         * in ways a self-reference check misses, and nothing legitimate nests this deeply anyway.
         */
        private static final int MAX_CAUSE_DEPTH = 10;

        private static DomainException unwrapDomain(Throwable throwable) {
            Throwable t = throwable;
            for (int depth = 0; t != null && depth < MAX_CAUSE_DEPTH; depth++, t = t.getCause()) {
                if (t instanceof DomainException domain) {
                    return domain;
                }
            }
            return null;
        }

        /** PostgreSQL reports a unique violation as SQLSTATE 23505, however deeply it is wrapped. */
        private static boolean isUniqueViolation(Throwable throwable, String constraint) {
            Throwable t = throwable;
            for (int depth = 0; t != null && depth < MAX_CAUSE_DEPTH; depth++, t = t.getCause()) {
                if (t instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                    String message = String.valueOf(sql.getMessage());
                    return message.contains(constraint);
                }
                if (t instanceof org.hibernate.exception.ConstraintViolationException hibernate
                        && constraint.equals(hibernate.getConstraintName())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isOptimisticLock(Throwable throwable) {
            Throwable t = throwable;
            for (int depth = 0; t != null && depth < MAX_CAUSE_DEPTH; depth++, t = t.getCause()) {
                if (t instanceof OptimisticLockException
                        || t instanceof org.hibernate.StaleStateException
                        || t instanceof jakarta.persistence.OptimisticLockException) {
                    return true;
                }
            }
            return false;
        }
    }
}
