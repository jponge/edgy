package org.acme.edgy.test.scatter.builtins.transformers;

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
import org.acme.edgy.runtime.builtins.transformers.responses.ResponseHeaderAdder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterResponseHeaderAdderTransformerTest {

    private static final String LEG_MARKER_HEADER = "X-Leg-Marker";

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-response-headers",
                            responses -> {
                                String headerValues = responses.stream()
                                        .map(r -> r.headers().get(LEG_MARKER_HEADER))
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(headerValues));
                            },
                            new Leg(Origin.of("s1", "http://localhost:8081/test/leg-a"))
                                    .addResponseTransformer(new ResponseHeaderAdder(LEG_MARKER_HEADER, "marker-a")),
                            new Leg(Origin.of("s2", "http://localhost:8081/test/leg-b"))
                                    .addResponseTransformer(new ResponseHeaderAdder(LEG_MARKER_HEADER, "marker-b"))))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/leg-a")
        public String legA() {
            return "from-a";
        }

        @GET
        @Path("/leg-b")
        public String legB() {
            return "from-b";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void responseTransformerAddsHeaderCapturedInLegResponse() {
        RestAssured.given()
                .get("/scatter-response-headers")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("marker-a|marker-b"));
    }
}
