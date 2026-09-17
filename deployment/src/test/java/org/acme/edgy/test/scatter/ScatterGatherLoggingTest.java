package org.acme.edgy.test.scatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.assertj.core.api.SoftAssertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterGatherLoggingTest {

    private static final String SCATTER_ROUTE_PATH = "/scatter";
    static final int WIREMOCK_PORT = 9091;
    private static WireMockServer wireMockServer;

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class))
            .overrideConfigKey("edgy.logging.enabled", "true")
            .setLogRecordPredicate(rec -> rec.getLoggerName().contains("LoggingProxyObserver"))
            .assertLogRecords(records -> {
                assertThat(records).as("Expected at least 4 log records from LoggingProxyObserver")
                        .hasSizeGreaterThanOrEqualTo(4);

                // Verify configuration logs for each leg
                var configRecords = records.stream()
                        .filter(rec -> {
                            String msg = String.format(rec.getMessage(), rec.getParameters());
                            return msg.contains("Configured scatter route");
                        })
                        .toList();

                assertThat(configRecords).as("Should log configuration for each leg").hasSize(2);

                SoftAssertions.assertSoftly(softly -> {
                    configRecords.forEach(rec -> {
                        String msg = String.format(rec.getMessage(), rec.getParameters());
                        softly.assertThat(msg).as("Config log should contain route path").contains(SCATTER_ROUTE_PATH);
                        softly.assertThat(msg).as("Config log should contain origin URI")
                                .containsAnyOf("http://localhost:9091/test/leg-a", "http://localhost:9091/test/leg-b");
                    });
                });

                // Verify request lifecycle logs
                assertThat(records)
                        .withFailMessage("No 'dispatching' log found")
                        .filteredOn(r -> String.format(r.getMessage(), r.getParameters()).contains("dispatching"))
                        .first()
                        .satisfies(dispatchRec -> {
                            String msg = String.format(dispatchRec.getMessage(), dispatchRec.getParameters());
                            SoftAssertions.assertSoftly(softly -> {
                                softly.assertThat(msg).as("Dispatch log should contain route path")
                                        .contains(SCATTER_ROUTE_PATH);
                                softly.assertThat(msg).as("Dispatch log should contain leg count")
                                        .contains("2 legs");
                            });
                        });

                assertThat(records)
                        .withFailMessage("No 'legs succeeded' log found")
                        .filteredOn(r -> String.format(r.getMessage(), r.getParameters()).contains("legs succeeded"))
                        .first()
                        .satisfies(completionRec -> {
                            String msg = String.format(completionRec.getMessage(), completionRec.getParameters());
                            SoftAssertions.assertSoftly(softly -> {
                                softly.assertThat(msg).as("Completion log should contain route path")
                                        .contains(SCATTER_ROUTE_PATH);
                                softly.assertThat(msg).as("Completion log should contain success count")
                                        .contains("2/2 legs succeeded");
                                softly.assertThat(msg).as("Completion log should contain duration")
                                        .contains("ms");
                            });
                        });
            });

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
    void scatterLoggingEmitsConfigAndLifecycle() {
        given()
                .get("/scatter")
                .then()
                .statusCode(StatusCode.OK);
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter",
                            responses -> {
                                String result = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("leg-a", "http://localhost:9091/test/leg-a")),
                            new Leg(Origin.of("leg-b", "http://localhost:9091/test/leg-b"))))
                    .build();
        }
    }
}
