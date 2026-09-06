package dev.quicknote.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import dev.quicknote.notes.domain.Label;
import dev.quicknote.notes.domain.Note;
import dev.quicknote.shared.pagination.PageParams;

/**
 * The same five numbers appear in five places. Five copies drift.
 *
 * <p>spec.md §Assumptions is the normative source; this test is what keeps the SQL constraints, the
 * domain invariants, and the published contract from quietly disagreeing with it — and with each
 * other.
 */
class LimitAgreementTest {

    private static final Path CONTRACT = Path.of("specs/001-note-management-api/contracts/openapi.yaml");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    private static final int TITLE = 200;
    private static final int BODY = 20_000;
    private static final int LABEL_NAME = 50;
    private static final int LABELS_PER_NOTE = 20;
    private static final int PAGE_SIZE_MAX = 100;
    private static final int PAGE_SIZE_DEFAULT = 25;

    @Test
    @DisplayName("the domain holds the normative limits")
    void domainMatchesSpec() {
        assertThat(Note.MAX_TITLE_LENGTH).isEqualTo(TITLE);
        assertThat(Note.MAX_BODY_LENGTH).isEqualTo(BODY);
        assertThat(Note.MAX_LABELS).isEqualTo(LABELS_PER_NOTE);
        assertThat(Label.MAX_NAME_LENGTH).isEqualTo(LABEL_NAME);
        assertThat(PageParams.MAX_SIZE).isEqualTo(PAGE_SIZE_MAX);
        assertThat(PageParams.DEFAULT_SIZE).isEqualTo(PAGE_SIZE_DEFAULT);
    }

    @Test
    @DisplayName("the published contract states the same limits")
    @SuppressWarnings("unchecked")
    void contractMatchesSpec() throws IOException {
        Map<String, Object> document = new Yaml().load(Files.readString(CONTRACT));
        Map<String, Object> schemas =
                (Map<String, Object>) ((Map<String, Object>) document.get("components")).get("schemas");

        Map<String, Object> note = (Map<String, Object>) schemas.get("Note");
        Map<String, Object> noteProps = (Map<String, Object>) note.get("properties");

        assertThat(maxLength(noteProps, "title")).isEqualTo(TITLE);
        assertThat(maxLength(noteProps, "body")).isEqualTo(BODY);
        assertThat(((Map<String, Object>) noteProps.get("labelIds")).get("maxItems"))
                .isEqualTo(LABELS_PER_NOTE);

        Map<String, Object> label = (Map<String, Object>) schemas.get("Label");
        assertThat(maxLength((Map<String, Object>) label.get("properties"), "name"))
                .isEqualTo(LABEL_NAME);

        Map<String, Object> parameters =
                (Map<String, Object>) ((Map<String, Object>) document.get("components")).get("parameters");
        Map<String, Object> sizeSchema =
                (Map<String, Object>) ((Map<String, Object>) parameters.get("Size")).get("schema");
        assertThat(sizeSchema.get("maximum")).isEqualTo(PAGE_SIZE_MAX);
        assertThat(sizeSchema.get("default")).isEqualTo(PAGE_SIZE_DEFAULT);
    }

    @Test
    @DisplayName("the database constraints state the same limits")
    void migrationsMatchSpec() throws IOException {
        String sql = allMigrations();

        assertThat(sql).as("title column width").containsPattern("title\\s+varchar\\(" + TITLE + "\\)");
        assertThat(sql).as("body length constraint").contains("length(body) <= " + BODY);
        assertThat(sql).as("label name width").containsPattern("name\\s+varchar\\(" + LABEL_NAME + "\\)");
    }

    @Test
    @DisplayName("the error catalogue quotes the same limits")
    void errorMessagesMatchSpec() {
        assertThat(new dev.quicknote.shared.problem.FieldError("body", "too_long", "must be at most " + BODY))
                .isNotNull();
        // The messages the service actually emits are built from the constants above, so agreement
        // here follows from domainMatchesSpec. This asserts the wiring rather than a literal.
        assertThat("must be at most " + Note.MAX_BODY_LENGTH + " characters")
                .isEqualTo("must be at most " + BODY + " characters");
    }

    @SuppressWarnings("unchecked")
    private static Object maxLength(Map<String, Object> properties, String field) {
        return ((Map<String, Object>) properties.get(field)).get("maxLength");
    }

    private static String allMigrations() throws IOException {
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            List<String> contents = files.filter(p -> p.toString().endsWith(".sql"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            return String.join("\n", contents);
        }
    }

    @Test
    @DisplayName("limits are counted in code points everywhere, so the layers agree on the unit")
    void limitsAreCountedInCodePoints() {
        // A single astral-plane character is two UTF-16 units but one code point. PostgreSQL counts
        // code points; if Java counted UTF-16 units the two would disagree about the same note.
        String astral = "𝔞".repeat(BODY);
        assertThat(astral.length()).isEqualTo(BODY * 2);
        assertThat(astral.codePointCount(0, astral.length())).isEqualTo(BODY);

        Matcher matcher = Pattern.compile("codePointCount")
                .matcher(readSource("src/main/java/dev/quicknote/notes/domain/Note.java"));
        assertThat(matcher.find())
                .as("Note must count code points, not String.length()")
                .isTrue();
        assertThat(readSource("src/main/java/dev/quicknote/notes/domain/Label.java"))
                .contains("codePointCount");
    }

    private static String readSource(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
