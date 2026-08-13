package org.acme.edgy.test.scatter;

import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
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

class ScatterGatherPredicateTest {

    static class RoutingProvider {
        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-pred")
                            .setPredicate(rc -> "true".equals(rc.request().getHeader("X-Scatter")))
                            .addLeg(new Leg(Origin.of("s1", "http://localhost:8081/test/p1"))
                                    .setMethod(HttpMethod.GET))
                            .addLeg(new Leg(Origin.of("s2", "http://localhost:8081/test/p2"))
                                    .setMethod(HttpMethod.GET))
                            .setComposer(responses -> {
                                String result = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("+"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            }))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/p1")
        public String p1() {
            return "p1";
        }

        @GET
        @Path("/p2")
        public String p2() {
            return "p2";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_predicate_match_returns_scatter_response() {
        RestAssured.given()
                .header("X-Scatter", "true")
                .get("/scatter-pred")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("p1+p2"));
    }

    @Test
    void test_predicate_mismatch_falls_through() {
        RestAssured.given()
                .get("/scatter-pred")
                .then()
                .statusCode(404);
    }
}
