package dev.quicknote.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import io.quarkus.security.UnauthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import dev.quicknote.notes.domain.UserId;

/**
 * The one place identity enters the application.
 *
 * <p>A proxyable request-scoped class rather than a producer of the {@link CurrentUser} record:
 * records are final, so CDI cannot build a client proxy for one, and singletons such as resources and
 * filters need a proxy to reach request state.
 */
@RequestScoped
public class CurrentUserProvider {

    @Inject
    JsonWebToken token;

    /**
     * The whole extraction rule, in one place and testable without a container.
     *
     * <p>There is deliberately no parameter for an identity supplied elsewhere in the request: the
     * {@code sub} claim is the only source, so no code path could prefer another.
     */
    public static CurrentUser from(JsonWebToken token) {
        String subject = token == null ? null : token.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException("Token carries no subject claim");
        }
        return new CurrentUser(UserId.of(subject));
    }

    public CurrentUser current() {
        return from(token);
    }

    public UserId id() {
        return current().id();
    }

    public String subject() {
        return current().subject();
    }
}
