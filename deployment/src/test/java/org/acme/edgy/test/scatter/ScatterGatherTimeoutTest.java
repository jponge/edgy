package org.acme.edgy.test.scatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.acme.edgy.runtime.api.utils.StatusCode.BAD_GATEWAY;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.FailureMode;
import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterGatherTimeoutTest {

    static final int WIREMOCK_PORT = 9091;
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT).gzipDisabled(true));
        wireMockServer.start();
        wireMockServer.stubFor(get(urlPathEqualTo("/test/fast"))
                .willReturn(aResponse().withBody("fast-response")));
        wireMockServer.stubFor(get(urlPathEqualTo("/test/slow"))
                .willReturn(aResponse().withFixedDelay(2000).withBody("slow-response")));
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    // scatter with FAIL_FAST mode - one slow leg
                    .addScatterRoute(new ScatterRoute("/scatter-fail-fast",
                            responses -> {
                                String result = responses.stream()
                                        .map(r -> r.body() != null
                                                ? r.body().toString()
                                                : "null")
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("fast", "http://localhost:9091/test/fast")),
                            new Leg(Origin.of("slow", "http://localhost:9091/test/slow")))
                            .setFailureMode(FailureMode.FAIL_FAST))
                    // scatter with PARTIAL mode - one slow leg
                    .addScatterRoute(new ScatterRoute("/scatter-partial",
                            responses -> {
                                String result = responses.stream()
                                        .filter(r -> r.body() != null)
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("fast2", "http://localhost:9091/test/fast")),
                            new Leg(Origin.of("slow2", "http://localhost:9091/test/slow")))
                            .setFailureMode(FailureMode.PARTIAL))
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .overrideConfigKey("edgy.origin.slow.idle-timeout", "1")
            .overrideConfigKey("edgy.origin.slow2.idle-timeout", "1")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class));

    @Test
    void scatterFailFastWhenOneLegTimesOut() {
        // fast leg: returns immediately
        // slow leg: 2000ms delay, idle-timeout 500ms -> timeout
        RestAssured.given()
                .get("/scatter-fail-fast")
                .then()
                .statusCode(BAD_GATEWAY);
    }

    @Test
    void scatterPartialWhenOneLegTimesOut() {
        // fast leg: returns immediately -> succeeds
        // slow leg: 2000ms delay, idle-timeout 500ms -> timeout
        // PARTIAL mode: returns only the successful leg's response
        RestAssured.given()
                .get("/scatter-partial")
                .then()
                .statusCode(OK)
                .body(is("fast-response"));
    }
}
