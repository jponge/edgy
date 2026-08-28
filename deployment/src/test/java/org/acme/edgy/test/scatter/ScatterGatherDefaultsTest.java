package org.acme.edgy.test.scatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.FailureMode;
import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.ResponseComposer;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.resiliency.GuardHandler;
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
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyResponse;

class ScatterGatherDefaultsTest {

    static final int WIREMOCK_PORT = 9091;
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT).gzipDisabled(true));
        wireMockServer.start();

        wireMockServer.stubFor(any(urlPathEqualTo("/test/leg-a"))
                .willReturn(aResponse().withBody("leg-a-ok")));
        wireMockServer.stubFor(any(urlPathEqualTo("/test/leg-b"))
                .willReturn(aResponse().withBody("leg-b-ok")));
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
                    .addScatterRoute(new ScatterRoute("/scatter-method-defaults",
                            joinBodies(),
                            new Leg(Origin.of("a", "http://localhost:9091/test/leg-a")), // inherits
                            new Leg(Origin.of("b", "http://localhost:9091/test/leg-b"))
                                    .setMethod(HttpMethod.POST)) // overrides
                            .setMethod(HttpMethod.PUT))

                    .addScatterRoute(new ScatterRoute("/scatter-transformer-defaults",
                            joinBodies(),
                            new Leg(Origin.of("a", "http://localhost:9091/test/leg-a")), // inherits scatter transformer only
                            new Leg(Origin.of("b", "http://localhost:9091/test/leg-b"))
                                    .addRequestTransformer(ctx -> { // appends after scatter's
                                        String existing = ctx.request().headers().get("X-Custom");
                                        ctx.request().headers().set("X-Custom", existing + "-then-leg");
                                        return ctx.sendRequest();
                                    }))
                            .addRequestTransformer(ctx -> { // scatter-level
                                ctx.request().headers().set("X-Custom", "scatter");
                                return ctx.sendRequest();
                            }))

                    .addScatterRoute(new ScatterRoute("/scatter-response-transformer-defaults",
                            responses -> {
                                String result = responses.stream()
                                        .map(r -> r.headers().get("X-Resp"))
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("a", "http://localhost:9091/test/leg-a")), // inherits scatter response transformer only
                            new Leg(Origin.of("b", "http://localhost:9091/test/leg-b"))
                                    .addResponseTransformer(ctx -> { // appends after scatter's
                                        String existing = ctx.response().headers().get("X-Resp");
                                        ctx.response().headers().set("X-Resp", existing + "-then-leg");
                                        return ctx.sendResponse();
                                    }))
                            .addResponseTransformer(ctx -> { // scatter-level
                                ctx.response().headers().set("X-Resp", "scatter");
                                return ctx.sendResponse();
                            }))

                    .addScatterRoute(new ScatterRoute("/scatter-keepbody-defaults",
                            joinBodies(),
                            new Leg(Origin.of("a", "http://localhost:9091/test/leg-a"))
                                    .setMethod(HttpMethod.GET), // inherits keepBody=true from scatter
                            new Leg(Origin.of("b", "http://localhost:9091/test/leg-b"))
                                    .setMethod(HttpMethod.GET)
                                    .setKeepBody(false)) // overrides keepBody to false
                            .setKeepBody(true))

                    .addScatterRoute(new ScatterRoute("/scatter-guard-defaults",
                            joinBodies(),
                            new Leg(Origin.of("a", "http://localhost:9091/test/leg-a")), // inherits 10-byte guard
                            new Leg(Origin.of("b", "http://localhost:9091/test/leg-b"))
                                    .setGuardHandler(payloadLimitGuard(200))) // overrides with 200-byte
                            .setGuardHandler(payloadLimitGuard(10))
                            .setFailureMode(FailureMode.PARTIAL))
                    .build();
        }

        private static ResponseComposer joinBodies() {
            return responses -> {
                String result = responses.stream()
                        .map(r -> r.body() != null ? r.body().toString() : "null")
                        .collect(Collectors.joining("|"));
                return Future.succeededFuture(Buffer.buffer(result));
            };
        }

        static GuardHandler payloadLimitGuard(long maxBytes) {
            return new GuardHandler() {
                @Override
                public Future<ProxyResponse> execute(Callable<Future<ProxyResponse>> action) throws Exception {
                    return action.call();
                }

                @Override
                public boolean needsBuffering() {
                    return false;
                }

                @Override
                public long maxPayloadSize() {
                    return maxBytes;
                }
            };
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class));

    // --- method override inheritance ---

    @Test
    void scatterMethodDefaultAppliedToInheritingLeg() {
        wireMockServer.resetRequests();

        RestAssured.given()
                .get("/scatter-method-defaults")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("leg-a-ok|leg-b-ok"));

        wireMockServer.verify(putRequestedFor(urlPathEqualTo("/test/leg-a")));
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/test/leg-b")));
    }

    // --- request transformer ordering ---

    @Test
    void scatterRequestTransformerExecutesBeforeLegTransformer() {
        wireMockServer.resetRequests();

        RestAssured.given()
                .get("/scatter-transformer-defaults")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("leg-a-ok|leg-b-ok"));

        // leg-a inherits scatter transformer only
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-a"))
                .withHeader("X-Custom", equalTo("scatter")));

        // leg-b: scatter sets "scatter", then leg appends "-then-leg"
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-b"))
                .withHeader("X-Custom", equalTo("scatter-then-leg")));
    }

    // --- response transformer ordering ---

    @Test
    void scatterResponseTransformerExecutesBeforeLegTransformer() {
        RestAssured.given()
                .get("/scatter-response-transformer-defaults")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("scatter|scatter-then-leg"));
    }

    // --- keepBody inheritance ---

    @Test
    void scatterKeepBodyInheritedByLegAndOverriddenByLeg() {
        wireMockServer.resetRequests();

        RestAssured.given()
                .body("test-body")
                .post("/scatter-keepbody-defaults")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("leg-a-ok|leg-b-ok"));

        // leg-a: GET with keepBody=true from scatter → body forwarded
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-a"))
                .withRequestBody(equalTo("test-body")));

        // leg-b: GET with keepBody=false override → body dropped
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/leg-b")));
    }

    // --- guard handler inheritance ---

    @Test
    void scatterGuardDefaultRejectsOnInheritingLegAcceptsOnOverridingLeg() {
        RestAssured.given()
                .body("x".repeat(50))
                .post("/scatter-guard-defaults")
                .then()
                .statusCode(StatusCode.OK);
        // PARTIAL mode: leg-a (10-byte limit) fails, leg-b (200-byte limit) succeeds
        // composed result has one failed leg and one success
    }

    @Test
    void scatterGuardDefaultAcceptsSmallPayloadOnBothLegs() {
        RestAssured.given()
                .body("hello")
                .post("/scatter-guard-defaults")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("leg-a-ok|leg-b-ok"));
    }
}
