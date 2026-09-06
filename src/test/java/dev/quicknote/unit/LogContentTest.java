package dev.quicknote.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Note content never reaches a log line.
 *
 * <p>Logs travel further than the database does — to aggregators, to screens, to support tickets. The
 * rule is that a log line carries identifiers and a correlation id, and nothing a user wrote.
 */
class LogContentTest {

    private static final Path MAIN = Path.of("src/main/java");

    /** Accessors whose return value is user-written content or credential material. */
    private static final List<String> FORBIDDEN_IN_LOGS =
            List.of(".title()", ".body()", ".name()", "getRawToken", "getToken()", "password", "credential");

    private static final Pattern LOG_CALL =
            Pattern.compile("LOG\\.(trace|debug|info|warn|error)f?\\(([^;]*);", Pattern.DOTALL);

    @Test
    @DisplayName("no log statement interpolates note content, label names, or token material")
    void logStatementsCarryNoUserContent() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher matcher = LOG_CALL.matcher(source);
                while (matcher.find()) {
                    String call = matcher.group(2);
                    for (String forbidden : FORBIDDEN_IN_LOGS) {
                        if (call.contains(forbidden)) {
                            offenders.add(file + " logs " + forbidden);
                        }
                    }
                }
            }
        }

        assertThat(offenders)
                .as("log identifiers and the correlation id, never what the user wrote")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan actually finds log statements, so an empty result means something")
    void scanIsNotVacuous() throws IOException {
        int logCalls = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = LOG_CALL.matcher(Files.readString(file));
                while (matcher.find()) {
                    logCalls++;
                }
            }
        }
        assertThat(logCalls).as("a test that scans nothing proves nothing").isGreaterThan(3);
    }

    @Test
    @DisplayName("every service log line carries the correlation id")
    void serviceLogsCarryCorrelation() throws IOException {
        List<String> withoutCorrelation = new ArrayList<>();
        Path applicationLayer = Path.of("src/main/java/dev/quicknote/notes/application");

        try (Stream<Path> files = Files.walk(applicationLayer)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher matcher = LOG_CALL.matcher(source);
                while (matcher.find()) {
                    if (!matcher.group(2).contains("CorrelationContext.current()")) {
                        withoutCorrelation.add(file + ": "
                                + matcher.group(2).lines().findFirst().orElse(""));
                    }
                }
            }
        }
        assertThat(withoutCorrelation)
                .as("a log line that cannot be tied back to a request is hard to use in an incident")
                .isEmpty();
    }
}
