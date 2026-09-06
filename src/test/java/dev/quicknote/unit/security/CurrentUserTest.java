package dev.quicknote.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import io.quarkus.security.UnauthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.security.CurrentUser;
import dev.quicknote.security.CurrentUserProvider;

class CurrentUserTest {

    /** A token stub carrying exactly the claims a test declares — nothing implicit. */
    private static JsonWebToken tokenWith(Map<String, Object> claims) {
        return new JsonWebToken() {
            @Override
            public String getName() {
                return (String) claims.get("sub");
            }

            @Override
            public Set<String> getClaimNames() {
                return claims.keySet();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getClaim(String claimName) {
                return (T) claims.get(claimName);
            }
        };
    }

    private static CurrentUser produce(JsonWebToken token) {
        return CurrentUserProvider.from(token);
    }

    @Test
    @DisplayName("identity comes from the sub claim")
    void identityFromSubject() {
        CurrentUser user = produce(tokenWith(Map.of("sub", "keycloak-uuid-1", "preferred_username", "alice")));
        assertThat(user.subject()).isEqualTo("keycloak-uuid-1");
        assertThat(user.id().value()).isEqualTo("keycloak-uuid-1");
    }

    @Test
    @DisplayName("a userId supplied anywhere else in the request is ignored")
    void ignoresOutOfBandIdentity() {
        // These are the shapes an attacker would try: a claim that looks like identity, and a
        // username. Neither is identity; only `sub` is.
        CurrentUser user = produce(tokenWith(Map.of(
                "sub", "real-subject",
                "userId", "attacker-supplied",
                "preferred_username", "bob",
                "email", "bob@example.test")));
        assertThat(user.subject())
                .as("only the sub claim is ever consulted")
                .isEqualTo("real-subject")
                .isNotEqualTo("attacker-supplied");
    }

    @Test
    @DisplayName("a token with no subject cannot be attributed to anyone")
    void rejectsSubjectlessToken() {
        assertThatThrownBy(() -> produce(tokenWith(Map.of("preferred_username", "alice"))))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> produce(tokenWith(Map.of("sub", "   ")))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("no token at all is refused")
    void rejectsMissingToken() {
        assertThatThrownBy(() -> produce(null)).isInstanceOf(UnauthorizedException.class);
    }
}
