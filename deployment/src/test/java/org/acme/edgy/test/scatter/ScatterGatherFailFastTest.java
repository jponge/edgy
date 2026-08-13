package org.acme.edgy.test.scatter;


import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.FailureMode;
import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpMethod;

class ScatterGatherFailFastTest {

    static class RoutingProvider {
        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-failfast")
                            .addLeg(new Leg(Origin.of("ok1", "http://localhost:8081/test/ok"))
                                    .setMethod(HttpMethod.GET))
                            .addLeg(new Leg(Origin.of("bad", "http://localhost:19999/nope"))
                                    .setMethod(HttpMethod.GET))
                            .setComposer(responses -> {
                                throw new AssertionError("Composer should not be called in fail-fast mode");
                            })
                            .setFailureMode(FailureMode.FAIL_FAST))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/ok")
        public String ok() {
            return "ok";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_failFast_returns502() {
        RestAssured.given()
                .get("/scatter-failfast")
                .then()
                .statusCode(502);
    }
}
