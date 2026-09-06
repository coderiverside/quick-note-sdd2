package dev.quicknote.notes.domain;

/**
 * The acting user, taken from the verified credential's {@code sub} claim.
 *
 * <p>There is no local users table: {@code sub} is a stable opaque identifier and is the only source
 * of identity accepted anywhere in this service.
 */
public record UserId(String value) {

    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId must not be blank");
        }
    }

    public static UserId of(String value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
