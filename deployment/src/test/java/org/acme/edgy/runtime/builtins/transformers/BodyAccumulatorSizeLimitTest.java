package org.acme.edgy.runtime.builtins.transformers;

import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.acme.edgy.runtime.api.utils.StatusCode.PAYLOAD_TOO_LARGE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.function.UnaryOperator;

import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.builtins.transformers.requests.RequestJsonObjectBodyModifier;
import org.acme.edgy.runtime.builtins.transformers.responses.ResponseJsonObjectBodyModifier;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

class BodyAccumulatorSizeLimitTest {

    private static final int MAX_BODY_SIZE = 16;

    static class RoutingProvider {
        @Produces
        @Singleton
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/request-limit",
                            Origin.of("origin-1", "http://localhost:8081/test/echo"))
                            .addRequestTransformer(
                                    new RequestJsonObjectBodyModifier(UnaryOperator.identity())))
                    .addRoute(new Route("/response-limit",
                            Origin.of("origin-2", "http://localhost:8081/test/large-response"))
                            .addResponseTransformer(
                                    new ResponseJsonObjectBodyModifier(UnaryOperator.identity())));
        }
    }

    @Path("/test")
    static class TestApi {
        @POST
        @Path("/echo")
        public String echo(String body) {
            return body;
        }

        @GET
        @Path("/large-response")
        public String largeResponse() {
            return new JsonObject().put("data", "a".repeat(MAX_BODY_SIZE + 1)).encode();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.http.limits.max-body-size", MAX_BODY_SIZE + "")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_bodyWithinLimit() {
        JsonObject small = new JsonObject().put("k", "v");
        RestAssured.given()
                .contentType("application/json")
                .body(small.encode())
                .post("/request-limit")
                .then()
                .statusCode(OK)
                .body(is(small.encode()));
    }

    @Test
    void test_bodyExceedsLimit() {
        // Uses response path — Vert.x enforces max-body-size on inbound requests:
        // https://github.com/quarkusio/quarkus/blob/main/extensions/vertx-http/runtime/src/main/java/io/quarkus/vertx/http/runtime/options/HttpServerCommonHandlers.java
        RestAssured.given()
                .get("/response-limit")
                .then()
                .statusCode(PAYLOAD_TOO_LARGE)
                .body(containsString("Body size exceeded"));
    }
}
