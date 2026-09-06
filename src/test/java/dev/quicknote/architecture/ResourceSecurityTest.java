package dev.quicknote.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.Path;

import io.quarkus.security.Authenticated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Constitution: every endpoint is authenticated unless it is explicitly declared public with a
 * written reason.
 *
 * <p>This is the mechanism that makes deny-by-default real. A resource added without
 * {@link Authenticated} fails the build rather than quietly shipping open.
 */
class ResourceSecurityTest {

    @Test
    @DisplayName("every resource class is authenticated, or explicitly and deliberately public")
    void everyResourceIsAuthenticated() throws IOException {
        List<Class<?>> resources = resourceClasses();
        assertThat(resources)
                .as("the scan must actually find resources, or it proves nothing")
                .isNotEmpty();

        List<String> unguarded = resources.stream()
                .filter(c -> !c.isAnnotationPresent(Authenticated.class))
                .filter(c -> !c.isAnnotationPresent(PermitAll.class))
                .map(Class::getName)
                .toList();

        assertThat(unguarded)
                .as("add @Authenticated, or @PermitAll with a comment saying why this is public")
                .isEmpty();
    }

    private static List<Class<?>> resourceClasses() throws IOException {
        java.nio.file.Path root = java.nio.file.Path.of("target/classes");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<java.nio.file.Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> root.relativize(p).toString())
                    .map(n -> n.replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", ""))
                    .filter(n -> !n.contains("$"))
                    .map(ResourceSecurityTest::load)
                    .filter(c -> c != null && c.isAnnotationPresent(Path.class))
                    .collect(java.util.stream.Collectors.<Class<?>>toList());
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, ResourceSecurityTest.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
