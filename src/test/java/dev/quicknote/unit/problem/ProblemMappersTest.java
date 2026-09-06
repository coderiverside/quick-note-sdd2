package dev.quicknote.unit.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.shared.problem.DomainException;
import dev.quicknote.shared.problem.ErrorCode;
import dev.quicknote.shared.problem.JsonProblemMappers;
import dev.quicknote.shared.problem.ProblemDetail;
import dev.quicknote.shared.problem.ProblemMappers;

/**
 * The mappers, exercised directly.
 *
 * <p>Their whole purpose is that nothing internal reaches a client, so they are worth testing in
 * isolation rather than only through the endpoints that happen to trigger them today.
 */
class ProblemMappersTest {

    private static ProblemDetail bodyOf(Response response) {
        assertThat(response.getMediaType().toString()).isEqualTo(ProblemMappers.PROBLEM_JSON);
        return (ProblemDetail) response.getEntity();
    }

    @Test
    @DisplayName("a domain failure keeps its code, status, and field errors")
    void domainFailure() {
        var mapper = new ProblemMappers.DomainExceptionMapper();
        Response response = mapper.toResponse(new DomainException.NoteTrashed());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(bodyOf(response).code()).isEqualTo("note_trashed");
    }

    @Test
    @DisplayName("a validation failure carries every offending field")
    void validationFailure() {
        var mapper = new ProblemMappers.DomainExceptionMapper();
        var failure = new DomainException.ValidationFailed(List.of(
                new dev.quicknote.shared.problem.FieldError("title", "too_long", "must be at most 200 characters")));

        ProblemDetail problem = bodyOf(mapper.toResponse(failure));
        assertThat(problem.code()).isEqualTo("validation_failed");
        assertThat(problem.errors()).singleElement().satisfies(e -> assertThat(e.field())
                .isEqualTo("title"));
    }

    @Test
    @DisplayName("a lost optimistic lock is a retryable conflict, not a server fault")
    void optimisticLock() {
        var mapper = new ProblemMappers.OptimisticLockMapper();
        Response response = mapper.toResponse(new OptimisticLockException("row changed"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(bodyOf(response).code()).isEqualTo("note_version_conflict");
        assertThat(bodyOf(response).detail()).doesNotContain("row changed");
    }

    @Test
    @DisplayName("authentication failures are indistinguishable and carry a challenge")
    void authenticationFailures() {
        Response failed = new ProblemMappers.AuthenticationMapper()
                .toResponse(new AuthenticationFailedException("expired signature"));
        Response unauthorized =
                new ProblemMappers.UnauthorizedMapper().toResponse(new UnauthorizedException("no subject"));

        for (Response response : List.of(failed, unauthorized)) {
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeaderString("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(bodyOf(response).code()).isEqualTo("unauthenticated");
            assertThat(bodyOf(response).detail())
                    .as("naming the failing check tells an attacker what to fix")
                    .isEqualTo("Authentication required.")
                    .doesNotContain("expired")
                    .doesNotContain("subject");
        }
    }

    @Test
    @DisplayName("framework failures become problem documents with a sensible code")
    void webApplicationFailures() {
        var mapper = new ProblemMappers.WebApplicationExceptionMapper();

        assertThat(bodyOf(mapper.toResponse(new NotFoundException())).code()).isEqualTo("note_not_found");
        assertThat(bodyOf(mapper.toResponse(new WebApplicationException(415))).code())
                .isEqualTo("malformed_request");
        assertThat(bodyOf(mapper.toResponse(new WebApplicationException(413))).code())
                .isEqualTo("payload_too_large");
        assertThat(bodyOf(mapper.toResponse(new WebApplicationException(429))).code())
                .isEqualTo("rate_limited");
        assertThat(bodyOf(mapper.toResponse(new WebApplicationException(503))).code())
                .isEqualTo("service_unavailable");
        assertThat(bodyOf(mapper.toResponse(new WebApplicationException(418))).code())
                .isEqualTo("internal_error");
    }

    @Test
    @DisplayName("an unhandled fault discloses nothing")
    void unhandledFault() {
        var mapper = new ProblemMappers.UnhandledMapper();
        Response response = mapper.toResponse(new IllegalStateException("SELECT * FROM notes WHERE secret = 'oops'"));

        assertThat(response.getStatus()).isEqualTo(500);
        ProblemDetail problem = bodyOf(response);
        assertThat(problem.code()).isEqualTo("internal_error");
        assertThat(problem.detail()).doesNotContain("SELECT").doesNotContain("oops");
        assertThat(problem.errors()).isNull();
    }

    @Test
    @DisplayName("a domain failure wrapped by the transaction manager is still reported as itself")
    void wrappedDomainFailureIsUnwrapped() {
        var mapper = new ProblemMappers.UnhandledMapper();
        Response response =
                mapper.toResponse(new RuntimeException("commit failed", new DomainException.LabelNameConflict()));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(bodyOf(response).code()).isEqualTo("label_name_conflict");
    }

    @Test
    @DisplayName("an optimistic lock raised at commit time is still a 409")
    void wrappedOptimisticLockIsUnwrapped() {
        var mapper = new ProblemMappers.UnhandledMapper();
        Response response = mapper.toResponse(new RuntimeException("rollback", new OptimisticLockException("stale")));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(bodyOf(response).code()).isEqualTo("note_version_conflict");
    }

    @Test
    @DisplayName("a unique-index violation on label names becomes a conflict")
    void uniqueViolationBecomesConflict() {
        var mapper = new ProblemMappers.UnhandledMapper();
        var sqlFailure = new java.sql.SQLException(
                "duplicate key value violates unique constraint \"ux_labels_owner_name\"", "23505");

        Response response = mapper.toResponse(new RuntimeException("flush failed", sqlFailure));
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(bodyOf(response).code()).isEqualTo("label_name_conflict");
    }

    @Test
    @DisplayName("a circular cause chain terminates instead of spinning")
    void circularCauseChainTerminates() {
        var mapper = new ProblemMappers.UnhandledMapper();
        // Java forbids self-causation, but a two-step cycle is easy to build by accident.
        var inner = new RuntimeException("inner");
        var outer = new RuntimeException("outer", inner);
        inner.initCause(outer);

        assertThat(mapper.toResponse(outer).getStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("a deeply wrapped domain failure is still found")
    void deeplyWrappedDomainFailure() {
        var mapper = new ProblemMappers.UnhandledMapper();
        Throwable wrapped = new DomainException.NoteTrashed();
        for (int i = 0; i < 5; i++) {
            wrapped = new RuntimeException("layer " + i, wrapped);
        }
        assertThat(mapper.toResponse(wrapped).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("a constraint violation is reported per field with a mapped code")
    void constraintViolations() {
        var mapper = new ProblemMappers.ConstraintViolationMapper();
        var violation = new StubViolation(
                "create.request.name", "must not be blank", jakarta.validation.constraints.NotBlank.class);

        Response response =
                mapper.toResponse(new jakarta.validation.ConstraintViolationException(java.util.Set.of(violation)));

        assertThat(response.getStatus()).isEqualTo(400);
        ProblemDetail problem = bodyOf(response);
        assertThat(problem.code()).isEqualTo("validation_failed");
        assertThat(problem.errors()).singleElement().satisfies(e -> {
            assertThat(e.field())
                    .as("the framework path is not the client's concern")
                    .isEqualTo("name");
            assertThat(e.code()).isEqualTo("blank");
        });
    }

    @Test
    @DisplayName("an unknown JSON property names the property and nothing internal")
    void unknownProperty() throws Exception {
        var mapper = new JsonProblemMappers();
        var exception =
                new UnrecognizedPropertyException(null, "Unrecognized field", null, Object.class, "isAdmin", null);

        Response response = mapper.unknownProperty(exception, null);
        assertThat(response.getStatus()).isEqualTo(400);
        ProblemDetail problem = (ProblemDetail) response.getEntity();
        assertThat(problem.code()).isEqualTo("malformed_request");
        assertThat(problem.detail()).contains("isAdmin").doesNotContain("java.lang.Object");
    }

    @Test
    @DisplayName("malformed JSON is reported without a parser trace")
    void malformedJson() {
        var mapper = new JsonProblemMappers();
        var exception = com.fasterxml.jackson.databind.exc.MismatchedInputException.from(
                (com.fasterxml.jackson.core.JsonParser) null, "unexpected token");

        ProblemDetail problem =
                (ProblemDetail) mapper.mismatchedInput(exception, null).getEntity();
        assertThat(problem.code()).isEqualTo("malformed_request");
        assertThat(problem.detail()).doesNotContain("unexpected token").doesNotContain("com.fasterxml");
    }

    @Test
    @DisplayName("each constraint annotation maps to its contractual field code")
    void constraintAnnotationsMapToFieldCodes() {
        var mapper = new ProblemMappers.ConstraintViolationMapper();

        record Case(Class<? extends java.lang.annotation.Annotation> annotation, String expected) {}
        for (Case testCase : List.of(
                new Case(jakarta.validation.constraints.NotNull.class, "required"),
                new Case(jakarta.validation.constraints.Size.class, "too_long"),
                new Case(jakarta.validation.constraints.NotEmpty.class, "blank"),
                new Case(jakarta.validation.constraints.Min.class, "out_of_range"),
                new Case(jakarta.validation.constraints.Max.class, "out_of_range"),
                new Case(jakarta.validation.constraints.Pattern.class, "invalid_format"))) {

            var violation = new StubViolation("create.request.field", "invalid", testCase.annotation());
            ProblemDetail problem = bodyOf(mapper.toResponse(
                    new jakarta.validation.ConstraintViolationException(java.util.Set.of(violation))));

            assertThat(problem.errors()).singleElement().satisfies(e -> assertThat(e.code())
                    .isEqualTo(testCase.expected()));
        }
    }

    @Test
    @DisplayName("a property path with no prefix is reported as-is")
    void bareePropertyPathIsUsedDirectly() {
        var mapper = new ProblemMappers.ConstraintViolationMapper();
        var violation = new StubViolation("name", "invalid", jakarta.validation.constraints.NotNull.class);

        ProblemDetail problem = bodyOf(
                mapper.toResponse(new jakarta.validation.ConstraintViolationException(java.util.Set.of(violation))));
        assertThat(problem.errors()).singleElement().satisfies(e -> assertThat(e.field())
                .isEqualTo("name"));
    }

    @Test
    @DisplayName("a framework 401 is reported as unauthenticated, not as a server fault")
    void frameworkUnauthorized() {
        var mapper = new ProblemMappers.WebApplicationExceptionMapper();
        ProblemDetail problem = bodyOf(mapper.toResponse(new WebApplicationException(401)));

        assertThat(problem.code()).isEqualTo("unauthenticated");
        assertThat(problem.status()).isEqualTo(401);
        assertThat(problem.detail()).isEqualTo("Authentication required.");
    }

    @Test
    @DisplayName("a framework 400 is reported as a malformed request")
    void frameworkBadRequest() {
        var mapper = new ProblemMappers.WebApplicationExceptionMapper();
        assertThat(bodyOf(mapper.toResponse(new WebApplicationException(400))).code())
                .isEqualTo("malformed_request");
        assertThat(bodyOf(mapper.toResponse(new WebApplicationException(406))).code())
                .isEqualTo("malformed_request");
    }

    @Test
    @DisplayName("every error code renders a well-formed problem document")
    void everyCodeRenders() {
        for (ErrorCode code : ErrorCode.values()) {
            ProblemDetail problem = ProblemDetail.of(code, "detail", "/v1/notes", "corr");
            assertThat(problem.status()).isEqualTo(code.status());
            assertThat(problem.code()).isEqualTo(code.code());
            assertThat(problem.type()).endsWith(code.code());
        }
    }

    /** A minimal violation: enough to exercise the mapping, with no mocking framework involved. */
    private record StubViolation(String path, String message, Class<? extends java.lang.annotation.Annotation> anno)
            implements jakarta.validation.ConstraintViolation<Object> {

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public String getMessageTemplate() {
            return message;
        }

        @Override
        public Object getRootBean() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<Object> getRootBeanClass() {
            return (Class<Object>) (Class<?>) Object.class;
        }

        @Override
        public Object getLeafBean() {
            return null;
        }

        @Override
        public Object[] getExecutableParameters() {
            return new Object[0];
        }

        @Override
        public Object getExecutableReturnValue() {
            return null;
        }

        @Override
        public jakarta.validation.Path getPropertyPath() {
            return new StubPath(path);
        }

        @Override
        public Object getInvalidValue() {
            return null;
        }

        @Override
        public jakarta.validation.metadata.ConstraintDescriptor<?> getConstraintDescriptor() {
            return new StubDescriptor(anno);
        }

        @Override
        public <U> U unwrap(Class<U> type) {
            throw new UnsupportedOperationException();
        }
    }

    private record StubPath(String value) implements jakarta.validation.Path {
        @Override
        public java.util.Iterator<Node> iterator() {
            return java.util.Collections.emptyIterator();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private record StubDescriptor(Class<? extends java.lang.annotation.Annotation> anno)
            implements jakarta.validation.metadata.ConstraintDescriptor<java.lang.annotation.Annotation> {

        @Override
        public java.lang.annotation.Annotation getAnnotation() {
            return (java.lang.annotation.Annotation) java.lang.reflect.Proxy.newProxyInstance(
                    anno.getClassLoader(), new Class<?>[] {anno}, (proxy, method, args) -> switch (method.getName()) {
                        case "annotationType" -> anno;
                        case "toString" -> "@" + anno.getName();
                        case "hashCode" -> 0;
                        case "equals" -> false;
                        default -> null;
                    });
        }

        @Override
        public String getMessageTemplate() {
            return "";
        }

        @Override
        public java.util.Set<Class<?>> getGroups() {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<Class<? extends jakarta.validation.Payload>> getPayload() {
            return java.util.Set.of();
        }

        @Override
        public jakarta.validation.ConstraintTarget getValidationAppliesTo() {
            return null;
        }

        @Override
        public java.util.List<
                        Class<? extends jakarta.validation.ConstraintValidator<java.lang.annotation.Annotation, ?>>>
                getConstraintValidatorClasses() {
            return java.util.List.of();
        }

        @Override
        public java.util.Map<String, Object> getAttributes() {
            return java.util.Map.of();
        }

        @Override
        public java.util.Set<jakarta.validation.metadata.ConstraintDescriptor<?>> getComposingConstraints() {
            return java.util.Set.of();
        }

        @Override
        public boolean isReportAsSingleViolation() {
            return false;
        }

        @Override
        public jakarta.validation.metadata.ValidateUnwrappedValue getValueUnwrapping() {
            return jakarta.validation.metadata.ValidateUnwrappedValue.DEFAULT;
        }

        @Override
        public <U> U unwrap(Class<U> type) {
            throw new UnsupportedOperationException();
        }
    }
}
