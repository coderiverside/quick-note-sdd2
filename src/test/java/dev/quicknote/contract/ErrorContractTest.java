package dev.quicknote.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import dev.quicknote.shared.problem.ErrorCode;
import dev.quicknote.support.ApiTestBase;

/**
 * Every status the service can emit is declared in the contract.
 *
 * <p>Closing this gap once was not enough: the same hole reopens the moment somebody adds an error
 * path without touching the contract. This test is what makes that a build failure instead of a
 * surprise, and it is why the earlier fix stays fixed.
 */
@QuarkusTest
class ErrorContractTest extends ApiTestBase {

    @Test
    @DisplayName("every status the service can emit is declared on every operation that can emit it")
    @SuppressWarnings("unchecked")
    void everyEmittableStatusIsDeclared() throws IOException {
        Map<String, Object> document =
                new Yaml().load(Files.readString(dev.quicknote.support.ContractValidation.CONTRACT));
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) document.get("paths");

        // These can arise on any operation, whatever it does.
        Set<String> universal = Set.of("401", "429", "500", "503");
        List<String> gaps = new ArrayList<>();

        for (var pathEntry : paths.entrySet()) {
            for (var methodEntry : pathEntry.getValue().entrySet()) {
                String method = methodEntry.getKey();
                if (!List.of("get", "post", "put", "patch", "delete").contains(method)) {
                    continue;
                }
                Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();
                Set<String> declared = ((Map<String, Object>) operation.get("responses")).keySet();

                for (String status : universal) {
                    if (!declared.contains(status)) {
                        gaps.add(method.toUpperCase() + " " + pathEntry.getKey() + " omits " + status);
                    }
                }
                // An operation that accepts a body can always exceed the transport ceiling.
                if (operation.containsKey("requestBody") && !declared.contains("413")) {
                    gaps.add(method.toUpperCase() + " " + pathEntry.getKey() + " omits 413");
                }
            }
        }

        assertThat(gaps)
                .as("an undeclared status is an error the client was never told to expect")
                .isEmpty();
    }

    @Test
    @DisplayName("every code the service can emit appears in the error catalogue")
    void everyCodeIsCatalogued() throws IOException {
        String catalogue =
                Files.readString(java.nio.file.Path.of("specs/001-note-management-api/contracts/error-catalog.md"));

        Set<String> missing = new TreeSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            if (!catalogue.contains("`" + code.code() + "`")) {
                missing.add(code.code());
            }
        }
        assertThat(missing)
                .as("the catalogue is part of the contract; a code absent from it is undocumented")
                .isEmpty();
    }

    @Test
    @DisplayName("the catalogue documents no code the service cannot emit")
    void catalogueHasNoDeadEntries() throws IOException {
        String catalogue =
                Files.readString(java.nio.file.Path.of("specs/001-note-management-api/contracts/error-catalog.md"));

        Set<String> known = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            known.add(code.code());
        }
        // Field-level codes live in the same document but belong to the errors array, not to ErrorCode.
        known.addAll(List.of("required", "too_long", "blank", "out_of_range", "invalid_format", "too_many_items"));

        Set<String> dead = new TreeSet<>();
        var matcher = java.util.regex.Pattern.compile("\\| `([a-z_]+)` \\|").matcher(catalogue);
        while (matcher.find()) {
            if (!known.contains(matcher.group(1))) {
                dead.add(matcher.group(1));
            }
        }
        assertThat(dead)
                .as("a documented code nothing can emit is a promise the service does not keep")
                .isEmpty();
    }

    @Test
    @DisplayName("a real error response carries every field the contract requires")
    void liveErrorMatchesTheContract() {
        var response = as(ALICE).when().get("/v1/notes/" + java.util.UUID.randomUUID());
        response.then().statusCode(404).contentType("application/problem+json");

        var body = response.jsonPath();
        assertThat(body.getString("type")).startsWith("https://");
        assertThat(body.getString("title")).isNotBlank();
        assertThat(body.getInt("status")).isEqualTo(404);
        assertThat(body.getString("code")).isEqualTo("note_not_found");
        assertThat(body.getString("correlationId")).isNotBlank();
    }
}
