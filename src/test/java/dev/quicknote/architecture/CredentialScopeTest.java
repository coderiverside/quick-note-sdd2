package dev.quicknote.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-005: this service verifies credentials and never issues, refreshes, or revokes them, and stores
 * no password.
 *
 * <p>A negative requirement is satisfied on day one and quietly violated on day ninety, when someone
 * adds a convenience login endpoint. This is the standing guard against that.
 */
class CredentialScopeTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path CONFIG = Path.of("src/main/resources/application.properties");

    private static final Pattern CREDENTIAL_ROUTE = Pattern.compile(
            "@Path\\s*\\(\\s*\"[^\"]*(token|login|logout|signin|sign-in|refresh|revoke|register|password)[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> FORBIDDEN_COLUMNS =
            List.of("password", "password_hash", "secret", "client_secret", "refresh_token", "access_token");

    @Test
    @DisplayName("no resource exposes a credential-issuing route")
    void noCredentialRoutes() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return CREDENTIAL_ROUTE.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
            assertThat(offenders)
                    .as("credential issuance belongs to the identity provider, not this service (FR-005)")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("no migration declares a credential column")
    void noCredentialColumns() throws IOException {
        if (!Files.isDirectory(MIGRATIONS)) {
            return;
        }
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".sql"))
                    .filter(p -> {
                        try {
                            String sql = Files.readString(p).toLowerCase(Locale.ROOT);
                            return FORBIDDEN_COLUMNS.stream().anyMatch(sql::contains);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
            assertThat(offenders)
                    .as("this service stores no credential material (FR-005)")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("OIDC is configured as a bearer-token service, never a web-app")
    void oidcIsServiceType() throws IOException {
        String config = Files.readString(CONFIG);
        assertThat(config)
                .as("web-app would add an authorization-code flow and sessions this service must not own")
                .contains("quarkus.oidc.application-type=service")
                .doesNotContain("application-type=web-app");
    }
}
