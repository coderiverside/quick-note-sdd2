package dev.quicknote.platform.health;

import java.sql.Connection;
import java.sql.Statement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness that actually verifies the dependencies.
 *
 * <p>A probe that returns UP because the process is running tells the load balancer nothing. This one
 * executes a real query, so a service that cannot serve traffic stops claiming it can.
 */
@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {

    private static final String NAME = "quick-note dependencies";

    @Inject
    AgroalDataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilderHolder holder = new HealthCheckResponseBuilderHolder();
        return holder.build(checkDatabase());
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return null;
        } catch (Exception e) {
            // The reason is safe to expose here: it names a dependency, never user data.
            return e.getClass().getSimpleName();
        }
    }

    /** Keeps the response shape in one place. */
    private static final class HealthCheckResponseBuilderHolder {
        HealthCheckResponse build(String databaseFailure) {
            var builder = HealthCheckResponse.named(NAME);
            if (databaseFailure == null) {
                return builder.up().withData("database", "reachable").build();
            }
            return builder.down()
                    .withData("database", "unreachable: " + databaseFailure)
                    .build();
        }
    }
}
