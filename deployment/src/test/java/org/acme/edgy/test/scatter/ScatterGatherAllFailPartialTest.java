package org.acme.edgy.test.scatter;

import static org.hamcrest.Matchers.is;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

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

class ScatterGatherAllFailPartialTest {

    static class RoutingProvider {
        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-allfail",
                            responses -> {
                                boolean allFailed = responses.stream().noneMatch(r -> r.succeeded());
                                if (allFailed) {
                                    return Future.succeededFuture(Buffer.buffer("all-failed-fallback"));
                                }
                                return Future.failedFuture(new RuntimeException("unexpected"));
                            },
                            new Leg(Origin.of("bad1", "http://localhost:19999/nope1")),
                            new Leg(Origin.of("bad2", "http://localhost:19998/nope2")))
                            .setFailureMode(FailureMode.PARTIAL))
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class));

    @Test
    void allFailPartialComposerReturnsFallback() {
        RestAssured.given()
                .get("/scatter-allfail")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("all-failed-fallback"));
    }
}
