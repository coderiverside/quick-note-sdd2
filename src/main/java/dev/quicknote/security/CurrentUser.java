package dev.quicknote.security;

import dev.quicknote.notes.domain.UserId;

/**
 * The acting user for this request.
 *
 * <p>Derived solely from the verified credential's {@code sub} claim. A user identifier supplied in a
 * body, query parameter, or any other header is ignored — there is no code path that reads one.
 */
public record CurrentUser(UserId id) {

    public String subject() {
        return id.value();
    }
}
