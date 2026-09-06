package dev.quicknote.notes.domain;

/**
 * A note is in exactly one of three states.
 *
 * <p>The spec describes {@code archived} and {@code trashed} as independent booleans, but its own
 * rules make most of the combinations impossible. Modelling one three-valued state makes the illegal
 * combinations unrepresentable rather than merely untested; the wire contract still carries the two
 * booleans, and the mapping is total.
 */
public enum NoteState {
    ACTIVE,
    ARCHIVED,
    TRASHED;

    public boolean isTrashed() {
        return this == TRASHED;
    }

    public boolean isArchived() {
        return this == ARCHIVED;
    }
}
