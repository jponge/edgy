package org.acme.edgy.test.scatter;

import static org.hamcrest.Matchers.is;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import org.acme.edgy.runtime.api.FailureMode;
import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.resiliency.SmallRyeFaultToleranceGuardHandler;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.Body;

class ScatterGatherGuardTest {

    static class RoutingProvider {
        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-guard")
                            .addLeg(new Leg(Origin.of("ok", "http://localhost:8081/test/healthy"))
                                    .setMethod(HttpMethod.GET))
                            .addLeg(new Leg(Origin.of("guarded", "http://localhost:8081/test/failing"))
                                    .setMethod(HttpMethod.GET)
                                    .setGuardHandler(
                                            SmallRyeFaultToleranceGuardHandler.builder()
                                                    .withCircuitBreaker(cb -> cb
                                                            .requestVolumeThreshold(1)
                                                            .failureRatio(0.5)
                                                            .delay(1, ChronoUnit.SECONDS))
                                                    .build(),
                                            StatusCode.SC_SUCCESS,
                                            (ctx, throwable) -> {
                                                var response = ctx.request().release().response()
                                                        .setBody(Body.body(Buffer.buffer("guard-fallback")))
                                                        .setStatusCode(StatusCode.OK);
                                                return Future.succeededFuture(response);
                                            }))
                            .setComposer(responses -> {
                                String result = responses.stream()
                                        .map(r -> r.body() != null ? r.body().toString() : "null")
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
        @Path("/healthy")
        public String healthy() {
            return "healthy";
        }

        @GET
        @Path("/failing")
        public Response failing() {
            return Response.status(500).entity("error").build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class))
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-smallrye-fault-tolerance-deployment",
                            System.getProperty("project.quarkus.version"))));

    @Test
    void test_guard_fallback_on_scatter_leg() {
        // First call: CB monitors, backend returns 500, expectation SC_SUCCESS considers it a failure
        RestAssured.given()
                .get("/scatter-guard")
                .then()
                .statusCode(StatusCode.OK);

        // Second call: CB may open and trigger fallback, or still pass through
        // After enough failures (requestVolumeThreshold=1 + failureRatio=0.5), fallback activates
        RestAssured.given()
                .get("/scatter-guard")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("healthy|guard-fallback"));
    }
}
