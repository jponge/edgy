package org.acme.edgy.test.scatter;

import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;

class ScatterGatherMethodOverrideTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-method-override",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("s1", "http://localhost:8081/test/post-to-put"))
                                    .setMethod(HttpMethod.PUT),
                            new Leg(Origin.of("s2", "http://localhost:8081/test/post-to-get-keep"))
                                    .setMethod(HttpMethod.GET)
                                    .setKeepBody(true)))
                    .addScatterRoute(new ScatterRoute("/scatter-method-drop-body",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("s3", "http://localhost:8081/test/post-to-get-drop"))
                                    .setMethod(HttpMethod.GET),
                            new Leg(Origin.of("s4", "http://localhost:8081/test/post-to-put"))
                                    .setMethod(HttpMethod.PUT)))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @PUT
        @Path("/post-to-put")
        public String putEndpoint(String body) {
            return "put-ok";
        }

        @GET
        @Path("/post-to-get-keep")
        public String getKeepEndpoint(String body) {
            return "get-keep-ok";
        }

        @GET
        @Path("/post-to-get-drop")
        public String getDropEndpoint() {
            return "get-drop-ok";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void legMethodOverrideChangesUpstreamMethod() {
        RestAssured.given()
                .body("test-payload")
                .post("/scatter-method-override")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("put-ok|get-keep-ok"));
    }

    @Test
    void legKeepBodyForwardsBodyWithOverriddenMethod() {
        // Verifies that leg2 with setMethod(GET) + setKeepBody(true) still receives the body
        // The composed result includes get-keep-ok, confirming the body was forwarded
        RestAssured.given()
                .contentType("text/plain")
                .body("keep-this-body")
                .post("/scatter-method-override")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("put-ok|get-keep-ok"));
    }

    @Test
    void legMethodOverrideStripsBodyForGet() {
        RestAssured.given()
                .body("test-payload")
                .post("/scatter-method-drop-body")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("get-drop-ok|put-ok"));
    }
}
