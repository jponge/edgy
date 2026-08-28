package org.acme.edgy.test.scatter.builtins.transformers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
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
import org.acme.edgy.runtime.builtins.transformers.requests.RequestQueryParameterRemover;
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

class ScatterRequestQueryParameterRemoverTest {

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
                    .addScatterRoute(new ScatterRoute("/scatter-qp-remove",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("s1", "http://localhost:9091/test/leg-a"))
                                    .addRequestTransformer(
                                            new RequestQueryParameterRemover("secret")),
                            new Leg(Origin.of("s2", "http://localhost:9091/test/leg-b"))
                                    .addRequestTransformer(
                                            new RequestQueryParameterRemover("secret", "internal"))))
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class));

    @Test
    void removerStripsQueryParamFromLegRequest() {
        RestAssured.given()
                .queryParam("secret", "token123")
                .queryParam("keep", "yes")
                .get("/scatter-qp-remove")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("leg-a|leg-b"));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-a"))
                .withQueryParam("secret", absent())
                .withQueryParam("keep", equalTo("yes")));
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-b"))
                .withQueryParam("secret", absent())
                .withQueryParam("keep", equalTo("yes")));
    }

    @Test
    void removerStripsMultipleQueryParamsFromLegRequest() {
        RestAssured.given()
                .queryParam("secret", "s")
                .queryParam("internal", "i")
                .queryParam("public", "p")
                .get("/scatter-qp-remove")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("leg-a|leg-b"));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-a"))
                .withQueryParam("secret", absent())
                .withQueryParam("public", equalTo("p")));
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-b"))
                .withQueryParam("secret", absent())
                .withQueryParam("internal", absent())
                .withQueryParam("public", equalTo("p")));
    }
}
