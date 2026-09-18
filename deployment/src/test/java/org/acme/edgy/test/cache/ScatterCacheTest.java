package org.acme.edgy.test.cache;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterCacheTest {

    private static final String CACHEABLE = "public, max-age=60";

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
                            new Leg(Origin.of("leg-a", "http://localhost:8081/test/leg-a")),
                            new Leg(Origin.of("leg-b", "http://localhost:8081/test/leg-b"))))
                    .addScatterRoute(new ScatterRoute("/scatter-distinct-uris",
                            responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            },
                            new Leg(Origin.of("leg-c", "http://localhost:8081/test/leg-c")),
                            new Leg(Origin.of("leg-d", "http://localhost:8081/test/leg-d"))))
                    .build();
        }
    }

    // both legs return cacheable responses; only leg-a has caching enabled
    @Path("/test")
    static class TestApi {

        static final AtomicInteger legAHits = new AtomicInteger();
        static final AtomicInteger legBHits = new AtomicInteger();
        static final AtomicInteger legCHits = new AtomicInteger();
        static final AtomicInteger legDHits = new AtomicInteger();

        @GET
        @Path("/leg-a")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> legA() {
            return cacheableResponse("a-" + legAHits.incrementAndGet());
        }

        @GET
        @Path("/leg-b")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> legB() {
            return cacheableResponse("b-" + legBHits.incrementAndGet());
        }

        @GET
        @Path("/leg-c")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> legC() {
            return cacheableResponse("c-" + legCHits.incrementAndGet());
        }

        @GET
        @Path("/leg-d")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> legD() {
            return cacheableResponse("d-" + legDHits.incrementAndGet());
        }

        private static RestResponse<String> cacheableResponse(String body) {
            return RestResponse.ResponseBuilder.ok(body, TEXT_PLAIN)
                    .header("Cache-Control", CACHEABLE)
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .overrideConfigKey("edgy.origin.leg-a.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.leg-c.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.leg-d.cache.enabled", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void onlyTheCachedLegIsServedFromCache() {
        RestAssured.get("/scatter")
                .then()
                .statusCode(OK)
                .body(is("a-1|b-1"));

        // leg-a replays its cached response, leg-b goes to the origin again
        RestAssured.get("/scatter")
                .then()
                .statusCode(OK)
                .body(is("a-1|b-2"));
    }

    @Test
    void scatterLegsWithDistinctUrisGetDistinctCaches() {
        RestAssured.get("/scatter-distinct-uris")
                .then()
                .statusCode(OK)
                .body(is("c-1|d-1"));

        RestAssured.get("/scatter-distinct-uris")
                .then()
                .statusCode(OK)
                .body(is("c-1|d-1"));
    }
}
