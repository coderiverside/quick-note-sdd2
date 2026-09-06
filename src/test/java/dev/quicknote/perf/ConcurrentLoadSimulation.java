package dev.quicknote.perf;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import java.time.Duration;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * SC-006: 500 concurrent users, mixed capture, listing, and search, with latency staying inside the
 * SC-002 targets and no request failing.
 *
 * <p>Run against a started instance, because a load test that shares a JVM with the service measures
 * the two of them together:
 *
 * <pre>
 *   docker compose up -d
 *   mvn gatling:test -Pperf \
 *     -Dquicknote.perf.baseUrl=http://localhost:8080 \
 *     -Dquicknote.perf.token=$TOKEN
 * </pre>
 */
public class ConcurrentLoadSimulation extends Simulation {

    private static final int CONCURRENT_USERS = 500;
    private static final Duration RAMP = Duration.ofSeconds(30);
    private static final Duration HOLD = Duration.ofMinutes(2);

    /** SC-002: the first page of a large collection inside one second at p95. */
    private static final int LISTING_P95_MILLIS = 1_000;

    /** The constitution's baseline for a single read. */
    private static final int READ_P95_MILLIS = 300;

    private final String baseUrl = System.getProperty("quicknote.perf.baseUrl", "http://localhost:8080");
    private final String token = System.getProperty("quicknote.perf.token", "");

    private final HttpProtocolBuilder protocol = http.baseUrl(baseUrl)
            .authorizationHeader("Bearer " + token)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json, application/problem+json")
            .shareConnections();

    private final ScenarioBuilder mixedWorkload = scenario("capture, list, and search")
            .exec(http("create note")
                    .post("/v1/notes")
                    .body(StringBody("{\"title\":\"load test\",\"body\":\"generated under load\"}"))
                    .check(status().is(201)))
            .exec(http("list notes").get("/v1/notes?size=25").check(status().is(200)))
            .exec(http("search notes").get("/v1/notes?q=generated&size=25").check(status().is(200)))
            .exec(http("list trash").get("/v1/trash?size=25").check(status().is(200)));

    {
        setUp(mixedWorkload
                        .injectOpen(rampUsers(CONCURRENT_USERS).during(RAMP), atOnceUsers(CONCURRENT_USERS / 10))
                        .protocols(protocol))
                .maxDuration(RAMP.plus(HOLD))
                .assertions(
                        // No request may fail: a 5xx or a dropped connection under load is a defect,
                        // not a capacity signal.
                        global().failedRequests().count().is(0L),
                        details("list notes").responseTime().percentile(95.0).lt(LISTING_P95_MILLIS),
                        details("search notes").responseTime().percentile(95.0).lt(LISTING_P95_MILLIS),
                        details("create note").responseTime().percentile(95.0).lt(5_000),
                        details("list trash").responseTime().percentile(95.0).lt(READ_P95_MILLIS * 4));
    }
}
