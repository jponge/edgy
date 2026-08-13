package org.acme.edgy.test.scatter;

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

class ScatterGatherComposerFailureTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter")
                            .addLeg(new Leg(Origin.of("s1", "http://localhost:8081/test/a")))
                            .addLeg(new Leg(Origin.of("s2", "http://localhost:8081/test/b")))
                            .setComposer(responses -> Future.failedFuture(new RuntimeException("composer failed"))))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/a")
        public String a() {
            return "from-a";
        }

        @GET
        @Path("/b")
        public String b() {
            return "from-b";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_scatterGather_composerFailure_returns502() {
        RestAssured.given()
                .post("/scatter")
                .then()
                .statusCode(StatusCode.BAD_GATEWAY);
    }
}
