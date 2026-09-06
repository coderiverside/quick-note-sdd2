package dev.quicknote.shared.problem;

/**
 * The stable, machine-readable error codes. These are part of the HTTP contract: a code may be added
 * within a version, but its meaning must not change and it must not be removed without a version
 * bump. See {@code contracts/error-catalog.md}.
 */
public enum ErrorCode {
    UNAUTHENTICATED("unauthenticated", 401, "Not authenticated"),

    NOTE_NOT_FOUND("note_not_found", 404, "Note not found"),
    LABEL_NOT_FOUND("label_not_found", 404, "Label not found"),

    VALIDATION_FAILED("validation_failed", 400, "Validation failed"),
    NOTE_CONTENT_REQUIRED("note_content_required", 400, "Note content required"),
    NOTE_COLOR_INVALID("note_color_invalid", 400, "Invalid colour"),
    MALFORMED_IDENTIFIER("malformed_identifier", 400, "Malformed identifier"),
    MALFORMED_REQUEST("malformed_request", 400, "Malformed request"),

    LABEL_NAME_CONFLICT("label_name_conflict", 409, "Label name already exists"),
    NOTE_TRASHED("note_trashed", 409, "Note is in the trash"),
    NOTE_VERSION_CONFLICT("note_version_conflict", 409, "Concurrent update conflict"),

    PAYLOAD_TOO_LARGE("payload_too_large", 413, "Payload too large"),
    NOTE_LABEL_LIMIT_EXCEEDED("note_label_limit_exceeded", 422, "Label limit exceeded"),
    RATE_LIMITED("rate_limited", 429, "Rate limit exceeded"),

    INTERNAL_ERROR("internal_error", 500, "Internal error"),
    SERVICE_UNAVAILABLE("service_unavailable", 503, "Service unavailable");

    private static final String TYPE_BASE = "https://api.quicknote.example/problems/";

    private final String code;
    private final int status;
    private final String title;

    ErrorCode(String code, int status, String title) {
        this.code = code;
        this.status = status;
        this.title = title;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String type() {
        return TYPE_BASE + code;
    }
}
