package dev.quicknote.shared.problem;

import java.util.List;

/**
 * Every failure this service reports deliberately. Sealed so the mapper is exhaustive and a new
 * failure mode cannot be added without deciding its contractual code.
 */
public sealed class DomainException extends RuntimeException {

    private final ErrorCode error;
    private final transient List<FieldError> fieldErrors;

    protected DomainException(ErrorCode error, String detail) {
        this(error, detail, List.of());
    }

    protected DomainException(ErrorCode error, String detail, List<FieldError> fieldErrors) {
        super(detail, null, false, false);
        this.error = error;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode error() {
        return error;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    /**
     * Absence and another user's resource are reported identically, so existence is never disclosed
     * (FR-008).
     */
    public static final class NoteNotFound extends DomainException {
        public NoteNotFound() {
            super(ErrorCode.NOTE_NOT_FOUND, "No such note.");
        }
    }

    public static final class LabelNotFound extends DomainException {
        public LabelNotFound() {
            super(ErrorCode.LABEL_NOT_FOUND, "No such label.");
        }
    }

    public static final class NoteContentRequired extends DomainException {
        public NoteContentRequired() {
            super(ErrorCode.NOTE_CONTENT_REQUIRED, "A note needs at least a title or a body.");
        }
    }

    public static final class NoteColorInvalid extends DomainException {
        public NoteColorInvalid(String permitted) {
            super(ErrorCode.NOTE_COLOR_INVALID, "Colour must be one of: " + permitted + ".");
        }
    }

    public static final class MalformedIdentifier extends DomainException {
        public MalformedIdentifier(String field) {
            super(
                    ErrorCode.MALFORMED_IDENTIFIER,
                    "Identifier is not well formed.",
                    List.of(new FieldError(field, FieldError.INVALID_FORMAT, "must be a UUID")));
        }
    }

    public static final class MalformedRequest extends DomainException {
        public MalformedRequest(String detail) {
            super(ErrorCode.MALFORMED_REQUEST, detail);
        }
    }

    public static final class ValidationFailed extends DomainException {
        public ValidationFailed(List<FieldError> errors) {
            super(
                    ErrorCode.VALIDATION_FAILED,
                    "The request contains " + errors.size() + " invalid field" + (errors.size() == 1 ? "" : "s") + ".",
                    errors);
        }
    }

    public static final class LabelNameConflict extends DomainException {
        public LabelNameConflict() {
            super(ErrorCode.LABEL_NAME_CONFLICT, "A label with this name already exists.");
        }
    }

    public static final class NoteTrashed extends DomainException {
        public NoteTrashed() {
            super(ErrorCode.NOTE_TRASHED, "The note is in the trash; restore it before changing it.");
        }
    }

    public static final class NoteVersionConflict extends DomainException {
        public NoteVersionConflict() {
            super(ErrorCode.NOTE_VERSION_CONFLICT, "The note changed concurrently; re-read it and retry.");
        }
    }

    public static final class NoteLabelLimitExceeded extends DomainException {
        public NoteLabelLimitExceeded(int max) {
            super(ErrorCode.NOTE_LABEL_LIMIT_EXCEEDED, "A note carries at most " + max + " labels.");
        }
    }
}
