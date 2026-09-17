package org.acme.edgy.test.scatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.FailureMode;
import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterGatherMetricsTest {

    private static final String SCATTER_ROUTE_PATH = "/scatter";
    static final int WIREMOCK_PORT = 9091;
    private static WireMockServer wireMockServer;

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-micrometer-registry-prometheus-deployment",
                    System.getProperty("project.quarkus.version"))));

    @Inject
    MeterRegistry registry;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT).gzipDisabled(true));
        wireMockServer.start();
        wireMockServer.stubFor(get(urlPathEqualTo("/test/leg-a"))
                .willReturn(aResponse().withBody("a")));
        wireMockServer.stubFor(get(urlPathEqualTo("/test/leg-b"))
                .willReturn(aResponse().withBody("b")));
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void scatterRequestRecordsTimerOnSuccess() {
        given()
                .get("/scatter")
                .then()
                .statusCode(StatusCode.OK);

        var search = registry.find("edgy.scatter.requests")
                .tag("route", SCATTER_ROUTE_PATH)
                .tag("outcome", "SUCCESS");

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Timer timer = search.timer();
            assertThat(timer).as("Timer should be registered for successful scatter request").isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).as("Duration should be recorded").isGreaterThan(0);
        });
    }

    @Test
    void scatterRequestRecordsTimerOnPartialFailure() {
        // Create a route with PARTIAL failure mode and one unreachable leg
        given()
                .get("/scatter-partial")
                .then()
                .statusCode(StatusCode.OK);

        var search = registry.find("edgy.scatter.requests")
                .tag("route", "/scatter-partial")
                .tag("outcome", "PARTIAL");

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Timer timer = search.timer();
            assertThat(timer).as("Timer should be registered for partial scatter request").isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).as("Duration should be recorded").isGreaterThan(0);
        });
    }

    @Test
    void scatterRequestRecordsTimerOnError() {
        // Create a route where both legs fail
        given()
                .get("/scatter-error")
                .then()
                .statusCode(StatusCode.BAD_GATEWAY);

        var search = registry.find("edgy.scatter.requests")
                .tag("route", "/scatter-error")
                .tag("outcome", "ERROR");

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Timer timer = search.timer();
            assertThat(timer).as("Timer should be registered for error scatter request").isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        });
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    // Success case: both legs succeed
                    .addScatterRoute(new ScatterRoute("/scatter",
                            responses -> {
                                String result = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("leg-a", "http://localhost:9091/test/leg-a")),
                            new Leg(Origin.of("leg-b", "http://localhost:9091/test/leg-b"))))
                    // Partial case: one leg fails but failureMode=PARTIAL allows success
                    .addScatterRoute(new ScatterRoute("/scatter-partial",
                            responses -> {
                                String result = responses.stream()
                                        .filter(r -> r.succeeded())
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("leg-a", "http://localhost:9091/test/leg-a")),
                            new Leg(Origin.of("unreachable", "http://localhost:1/nowhere")))
                            .setFailureMode(FailureMode.PARTIAL))
                    // Error case: both legs fail
                    .addScatterRoute(new ScatterRoute("/scatter-error",
                            responses -> {
                                String result = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("unreachable-1", "http://localhost:1/nowhere1")),
                            new Leg(Origin.of("unreachable-2", "http://localhost:1/nowhere2"))))
                    .build();
        }
    }
}
