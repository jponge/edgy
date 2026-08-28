package org.acme.edgy.test.scatter.builtins.transformers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.acme.edgy.runtime.builtins.transformers.requests.RequestQueryParameterAdder;
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

class ScatterRequestQueryParameterAdderTest {

    static final int WIREMOCK_PORT = 9091;
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT).gzipDisabled(true));
        wireMockServer.start();
        wireMockServer.stubFor(get(urlPathEqualTo("/test/leg-a"))
                .willReturn(aResponse().withBody("leg-a")));
        wireMockServer.stubFor(get(urlPathEqualTo("/test/leg-b"))
                .willReturn(aResponse().withBody("leg-b")));
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
                    .addScatterRoute(new ScatterRoute("/scatter-qp-add",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("s1", "http://localhost:9091/test/leg-a"))
                                    .addRequestTransformer(
                                            new RequestQueryParameterAdder("source", "leg-a")),
                            new Leg(Origin.of("s2", "http://localhost:9091/test/leg-b"))
                                    .addRequestTransformer(
                                            new RequestQueryParameterAdder("source", "leg-b"))
                                    .addRequestTransformer(
                                            new RequestQueryParameterAdder("extra", "val"))))
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class));

    @Test
    void adderInjectsDistinctQueryParamPerLeg() {
        RestAssured.given()
                .get("/scatter-qp-add")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("leg-a|leg-b"));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-a"))
                .withQueryParam("source", equalTo("leg-a")));
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-b"))
                .withQueryParam("source", equalTo("leg-b"))
                .withQueryParam("extra", equalTo("val")));
    }

    @Test
    void adderPreservesPropagatedQueryParams() {
        RestAssured.given()
                .queryParam("foo", "bar")
                .get("/scatter-qp-add")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("leg-a|leg-b"));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-a"))
                .withQueryParam("foo", equalTo("bar"))
                .withQueryParam("source", equalTo("leg-a")));
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-b"))
                .withQueryParam("foo", equalTo("bar"))
                .withQueryParam("source", equalTo("leg-b")));
    }
}
