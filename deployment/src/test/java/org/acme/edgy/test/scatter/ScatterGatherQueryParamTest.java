package org.acme.edgy.test.scatter;

import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

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

class ScatterGatherQueryParamTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("s1", "http://localhost:8081/test/leg-a")),
                            new Leg(Origin.of("s2", "http://localhost:8081/test/leg-b"))))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/leg-a")
        public String legA(@QueryParam("foo") String foo,
                @QueryParam("a") String a,
                @QueryParam("b") String b) {
            // Return "from-leg-a" to match the expected response pattern
            return "from-leg-a";
        }

        @GET
        @Path("/leg-b")
        public String legB(@QueryParam("foo") String foo,
                @QueryParam("a") String a,
                @QueryParam("b") String b) {
            return "from-leg-b";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void queryParamsArePropagatedToAllLegs() {
        RestAssured.given()
                .queryParam("foo", "bar")
                .get("/scatter")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("from-leg-a|from-leg-b"));
    }

    @Test
    void multipleQueryParamsArePropagated() {
        RestAssured.given()
                .queryParam("a", "1")
                .queryParam("b", "2")
                .get("/scatter")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("from-leg-a|from-leg-b"));
    }
}
