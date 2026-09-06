package dev.quicknote.shared.problem;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 9457 Problem Details, extended with the stable {@code code} the constitution requires.
 *
 * <p>Nothing internal ever reaches this object: no stack trace, no SQL fragment, no hostname, no
 * upstream error text. The full detail is logged against the same correlation id instead.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String correlationId,
        List<FieldError> errors) {

    public static ProblemDetail of(ErrorCode error, String detail, String instance, String correlationId) {
        return new ProblemDetail(
                error.type(), error.title(), error.status(), detail, instance, error.code(), correlationId, null);
    }

    public static ProblemDetail validation(
            ErrorCode error, String detail, String instance, String correlationId, List<FieldError> errors) {
        return new ProblemDetail(
                error.type(),
                error.title(),
                error.status(),
                detail,
                instance,
                error.code(),
                correlationId,
                errors == null || errors.isEmpty() ? null : List.copyOf(errors));
    }
}
