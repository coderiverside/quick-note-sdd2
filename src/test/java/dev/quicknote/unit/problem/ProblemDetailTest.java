package dev.quicknote.unit.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.shared.problem.DomainException;
import dev.quicknote.shared.problem.ErrorCode;
import dev.quicknote.shared.problem.FieldError;
import dev.quicknote.shared.problem.ProblemDetail;

class ProblemDetailTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("carries the RFC 9457 field set plus the stable code")
    void rfc9457FieldSet() throws Exception {
        ProblemDetail problem = ProblemDetail.of(ErrorCode.NOTE_NOT_FOUND, "No such note.", "/v1/notes/x", "corr-1");
        JsonNode json = mapper.readTree(mapper.writeValueAsString(problem));

        assertThat(json.get("type").asText()).isEqualTo("https://api.quicknote.example/problems/note_not_found");
        assertThat(json.get("title").asText()).isEqualTo("Note not found");
        assertThat(json.get("status").asInt()).isEqualTo(404);
        assertThat(json.get("detail").asText()).isEqualTo("No such note.");
        assertThat(json.get("instance").asText()).isEqualTo("/v1/notes/x");
        assertThat(json.get("code").asText()).isEqualTo("note_not_found");
        assertThat(json.get("correlationId").asText()).isEqualTo("corr-1");
    }

    @Test
    @DisplayName("absent fields are omitted rather than serialized as null")
    void omitsAbsentFields() throws Exception {
        ProblemDetail problem = ProblemDetail.of(ErrorCode.RATE_LIMITED, null, null, null);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(problem));
        assertThat(json.has("detail")).isFalse();
        assertThat(json.has("errors")).isFalse();
        assertThat(json.has("correlationId")).isFalse();
    }

    @Test
    @DisplayName("a validation failure names every offending field")
    void validationNamesFields() throws Exception {
        var errors = List.of(
                new FieldError("title", FieldError.TOO_LONG, "must be at most 200 characters"),
                new FieldError("color", FieldError.INVALID_FORMAT, "must be one of: default, red"));
        ProblemDetail problem =
                ProblemDetail.validation(ErrorCode.VALIDATION_FAILED, "2 invalid fields.", "/v1/notes", "c", errors);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(problem));

        assertThat(json.get("errors")).hasSize(2);
        assertThat(json.get("errors").get(0).get("field").asText()).isEqualTo("title");
        assertThat(json.get("errors").get(0).get("code").asText()).isEqualTo("too_long");
    }

    @Test
    @DisplayName("no internal detail can reach a client: exceptions carry no stack trace at all")
    void noStackTraceLeaks() throws Exception {
        DomainException exception = new DomainException.NoteTrashed();

        assertThat(exception.getStackTrace())
                .as("suppressed at construction, so it cannot be serialized even by accident")
                .isEmpty();

        ProblemDetail problem = ProblemDetail.of(exception.error(), exception.getMessage(), "/v1/notes/x", "corr-2");
        String json = mapper.writeValueAsString(problem);
        assertThat(json)
                .doesNotContain("dev.quicknote")
                .doesNotContain("java.lang")
                .doesNotContain("at ")
                .doesNotContain("SELECT")
                .doesNotContain("Caused by");
    }

    @Test
    @DisplayName("every catalogued code has a distinct identifier and a sane status")
    void catalogueIsWellFormed() {
        assertThat(ErrorCode.values()).extracting(ErrorCode::code).doesNotHaveDuplicates();
        assertThat(ErrorCode.values()).allSatisfy(c -> {
            assertThat(c.status()).isBetween(400, 599);
            assertThat(c.code()).matches("[a-z_]+");
            assertThat(c.type()).startsWith("https://");
            assertThat(c.title()).isNotBlank();
        });
    }

    @Test
    @DisplayName("there is no 403 in this API: ownership failures are reported as absence")
    void noForbiddenCode() {
        assertThat(ErrorCode.values())
                .as("returning 403 for another user's note would confirm that note exists (FR-008)")
                .noneMatch(c -> c.status() == 403);
        assertThat(new DomainException.NoteNotFound().error().status()).isEqualTo(404);
        assertThat(new DomainException.LabelNotFound().error().status()).isEqualTo(404);
    }
}
