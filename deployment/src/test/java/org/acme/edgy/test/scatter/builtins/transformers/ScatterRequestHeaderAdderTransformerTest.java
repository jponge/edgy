package org.acme.edgy.test.scatter.builtins.transformers;

import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.acme.edgy.runtime.builtins.transformers.requests.RequestHeaderAdder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterRequestHeaderAdderTransformerTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-request-headers",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("s1", "http://localhost:8081/test/leg-a"))
                                    .addRequestTransformer(new RequestHeaderAdder("X-Custom-A", "injected-a")),
                            new Leg(Origin.of("s2", "http://localhost:8081/test/leg-b"))
                                    .addRequestTransformer(new RequestHeaderAdder("X-Custom-B", "injected-b"))))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/leg-a")
        public String legA(@HeaderParam("X-Custom-A") String customA) {
            return customA != null ? "from-a:" + customA : "from-a";
        }

        @GET
        @Path("/leg-b")
        public String legB(@HeaderParam("X-Custom-B") String customB) {
            return customB != null ? "from-b:" + customB : "from-b";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void requestTransformerAddsHeaderToLegRequest() {
        RestAssured.given()
                .get("/scatter-request-headers")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("from-a:injected-a|from-b:injected-b"));
    }
}
