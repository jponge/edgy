package org.acme.edgy.test.scatter;

import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

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

class ScatterGatherSegmentTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter/{param}",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("s1", "http://localhost:8081/test/{param}/a")),
                            new Leg(Origin.of("s2", "http://localhost:8081/test/{param}/b"))))
                    .addScatterRoute(new ScatterRoute("/scatter-wild/*",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("w1", "http://localhost:8081/wild/{suffix}/leg-a")),
                            new Leg(Origin.of("w2", "http://localhost:8081/wild/{suffix}/leg-b"))))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/{param}/a")
        public String legA(@PathParam("param") String param) {
            return "a-" + param;
        }

        @GET
        @Path("/{param}/b")
        public String legB(@PathParam("param") String param) {
            return "b-" + param;
        }
    }

    @Path("/wild")
    static class WildcardApi {

        @GET
        @Path("/{suffix}/leg-a")
        public String wildcardLegA(@PathParam("suffix") String suffix) {
            return "wild-a";
        }

        @GET
        @Path("/{suffix}/leg-b")
        public String wildcardLegB(@PathParam("suffix") String suffix) {
            return "wild-b";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class, WildcardApi.class));

    @Test
    void scatterWithPathParameterRewritesLegOrigins() {
        RestAssured.given()
                .get("/scatter/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("a-hello|b-hello"));
    }

    @Test
    void scatterWithPathParameterRewritesLegOrigins_differentParam() {
        RestAssured.given()
                .get("/scatter/world")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("a-world|b-world"));
    }

    @Test
    void scatterWithWildcardPropagatesPath() {
        RestAssured.given()
                .get("/scatter-wild/mysuffix")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("wild-a|wild-b"));
    }
}
