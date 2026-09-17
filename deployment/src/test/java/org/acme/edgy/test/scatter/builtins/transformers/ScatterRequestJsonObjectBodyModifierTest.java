package org.acme.edgy.test.scatter.builtins.transformers;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.acme.edgy.runtime.builtins.transformers.requests.RequestJsonObjectBodyModifier;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterRequestJsonObjectBodyModifierTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-json",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("s1", "http://localhost:8081/test/leg-add"))
                                    .addRequestTransformer(new RequestJsonObjectBodyModifier(json -> {
                                        json.put("injected", "leg-a");
                                        return json;
                                    })),
                            new Leg(Origin.of("s2", "http://localhost:8081/test/leg-remove"))
                                    .addRequestTransformer(new RequestJsonObjectBodyModifier(json -> {
                                        json.remove("removeMe");
                                        return json;
                                    }))))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @POST
        @Path("/leg-add")
        @Consumes(APPLICATION_JSON)
        public String legAdd(String body) {
            return body;
        }

        @POST
        @Path("/leg-remove")
        @Consumes(APPLICATION_JSON)
        public String legRemove(String body) {
            return body;
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void jsonBodyModifierAddsFieldToLeg() {
        RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body("{\"key\":\"value\",\"removeMe\":\"gone\"}")
                .post("/scatter-json")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("{\"key\":\"value\",\"removeMe\":\"gone\",\"injected\":\"leg-a\"}"
                        + "|{\"key\":\"value\"}"));
    }

    @Test
    void jsonBodyModifierHandlesNestedObject() {
        RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body("{\"nested\":{\"a\":1},\"removeMe\":\"x\"}")
                .post("/scatter-json")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("{\"nested\":{\"a\":1},\"removeMe\":\"x\",\"injected\":\"leg-a\"}"
                        + "|{\"nested\":{\"a\":1}}"));
    }
}
