package dev.quicknote.support;

import java.nio.file.Files;
import java.nio.file.Path;

import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;

/**
 * Every integration response is validated against the hand-authored contract.
 *
 * <p>The authored document is the source of truth, per Constitution Principle I: a response shape
 * that is not in the contract fails the test, rather than the contract being regenerated to match
 * whatever the code happened to produce.
 */
public final class ContractValidation {

    public static final Path CONTRACT = Path.of("specs/001-note-management-api/contracts/openapi.yaml");

    private static final OpenApiValidationFilter FILTER = build();

    private ContractValidation() {}

    private static OpenApiValidationFilter build() {
        if (!Files.isReadable(CONTRACT)) {
            throw new IllegalStateException("Authored contract not found at " + CONTRACT.toAbsolutePath()
                    + ". Contract-first is not optional; the contract must exist before the code.");
        }
        return new OpenApiValidationFilter(CONTRACT.toAbsolutePath().toString());
    }

    /** Attach to a RestAssured request to assert conformance of both request and response. */
    public static OpenApiValidationFilter filter() {
        return FILTER;
    }
}
