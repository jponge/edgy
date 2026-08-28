package org.acme.edgy.test.scatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
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

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterGatherPartialTest {

    static final int WIREMOCK_PORT = 9091;
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT).gzipDisabled(true));
        wireMockServer.start();
        wireMockServer.stubFor(get(urlPathEqualTo("/test/alpha"))
                .willReturn(aResponse().withBody("alpha")));
        wireMockServer.stubFor(get(urlPathEqualTo("/test/beta"))
                .willReturn(aResponse().withBody("beta")));
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
                    .addScatterRoute(new ScatterRoute("/scatter-partial",
                            responses -> {
                                String result = responses.stream()
                                        .map(r -> r.succeeded()
                                                ? r.body().toString()
                                                : "FAILED:" + r.originIdentifier())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("ok1",
                                    "http://localhost:9091/test/alpha")),
                            new Leg(Origin.of("bad", "http://localhost:19999/nope")),
                            new Leg(Origin.of("ok2",
                                    "http://localhost:9091/test/beta")))
                            .setFailureMode(FailureMode.PARTIAL))
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class));

    @Test
    void partialModeDeliversFailuresToComposer() {
        RestAssured.given()
                .get("/scatter-partial")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("alpha|FAILED:bad|beta"));
    }
}
