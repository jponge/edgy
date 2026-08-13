package org.acme.edgy.test.scatter;

import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.FailureMode;
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

class ScatterGatherPartialTest {

    static class RoutingProvider {
        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-partial")
                            .addLeg(new Leg(Origin.of("ok1", "http://localhost:8081/test/alpha"))
                                    .setMethod(HttpMethod.GET))
                            .addLeg(new Leg(Origin.of("bad", "http://localhost:19999/nope"))
                                    .setMethod(HttpMethod.GET))
                            .addLeg(new Leg(Origin.of("ok2", "http://localhost:8081/test/beta"))
                                    .setMethod(HttpMethod.GET))
                            .setComposer(responses -> {
                                String result = responses.stream()
                                        .map(r -> r.succeeded() ? r.body().toString() : "FAILED:" + r.originIdentifier())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            })
                            .setFailureMode(FailureMode.PARTIAL))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/alpha")
        public String alpha() {
            return "alpha";
        }

        @GET
        @Path("/beta")
        public String beta() {
            return "beta";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_partial_mode_delivers_failures_to_composer() {
        RestAssured.given()
                .get("/scatter-partial")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("alpha|FAILED:bad|beta"));
    }
}
