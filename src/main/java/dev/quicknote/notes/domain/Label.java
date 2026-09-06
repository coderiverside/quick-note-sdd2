package dev.quicknote.notes.domain;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import dev.quicknote.shared.problem.DomainException;
import dev.quicknote.shared.problem.FieldError;

/**
 * A user-defined tag for grouping notes.
 *
 * <p>Names are unique per user, compared case-insensitively and ignoring surrounding whitespace, but
 * the casing the user chose is preserved for display. The comparison key and the stored value are
 * deliberately different things.
 */
public final class Label {

    public static final int MAX_NAME_LENGTH = 50;

    private final LabelId id;
    private final UserId ownerId;
    private final Instant createdAt;
    private String name;

    private Label(LabelId id, UserId ownerId, String name, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = normalize(name);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Label create(UserId ownerId, String name) {
        return new Label(LabelId.newId(), ownerId, name, Instant.now());
    }

    public static Label rehydrate(LabelId id, UserId ownerId, String name, Instant createdAt) {
        return new Label(id, ownerId, name, createdAt);
    }

    public void rename(String newName) {
        this.name = normalize(newName);
    }

    public LabelId id() {
        return id;
    }

    public UserId ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isOwnedBy(UserId candidate) {
        return ownerId.equals(candidate);
    }

    /** The key uniqueness is judged on — never what is stored or shown. */
    public String comparisonKey() {
        return comparisonKeyOf(name);
    }

    public static String comparisonKeyOf(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new DomainException.ValidationFailed(
                    List.of(new FieldError("name", FieldError.BLANK, "must not be blank")));
        }
        // Code points, to agree with PostgreSQL's length() rather than Java's UTF-16 units.
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_NAME_LENGTH) {
            throw new DomainException.ValidationFailed(List.of(
                    new FieldError("name", FieldError.TOO_LONG, "must be at most " + MAX_NAME_LENGTH + " characters")));
        }
        return trimmed;
    }
}
