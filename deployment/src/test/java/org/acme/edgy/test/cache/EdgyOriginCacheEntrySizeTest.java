package org.acme.edgy.test.cache;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * The cache bounds stored bodies by its own {@code max-entry-size}, which is
 * independent of {@code quarkus.http.limits.max-body-size} - that one bounds
 * inbound request bodies and may be configured smaller.
 */
class EdgyOriginCacheEntrySizeTest {

    private static final String CACHEABLE = "public, max-age=60";
    private static final int BODY_BYTES = 4096;

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/within-cap",
                            Origin.of("within-cap", "http://localhost:8081/test/within-cap")))
                    .addRoute(new Route("/over-cap",
                            Origin.of("over-cap", "http://localhost:8081/test/over-cap")))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        static final AtomicInteger withinCapHits = new AtomicInteger();
        static final AtomicInteger overCapHits = new AtomicInteger();

        @GET
        @Path("/within-cap")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> withinCap() {
            return cacheableResponse(body(withinCapHits.incrementAndGet()));
        }

        @GET
        @Path("/over-cap")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> overCap() {
            return cacheableResponse(body(overCapHits.incrementAndGet()));
        }

        // a fixed-length body whose first character identifies the origin call
        private static String body(int hit) {
            return hit + "x".repeat(BODY_BYTES - 1);
        }

        private static RestResponse<String> cacheableResponse(String body) {
            return RestResponse.ResponseBuilder.ok(body, TEXT_PLAIN)
                    .header("Cache-Control", CACHEABLE)
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            // deliberately below both origins' bodies, to prove the cache does
            // not borrow this limit
            .overrideConfigKey("quarkus.http.limits.max-body-size", "1K")
            .overrideConfigKey("edgy.origin.within-cap.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.within-cap.cache.max-entry-size", "8K")
            .overrideConfigKey("edgy.origin.over-cap.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.over-cap.cache.max-entry-size", "2K")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void bodyWithinTheEntryCapIsCachedEvenWhenItExceedsTheInboundBodyLimit() {
        RestAssured.get("/within-cap").then().statusCode(OK).body(is(TestApi.body(1)));

        RestAssured.get("/within-cap").then().statusCode(OK).body(is(TestApi.body(1)));
    }

    @Test
    void bodyOverTheEntryCapIsProxiedUncachedRatherThanFailing() {
        RestAssured.get("/over-cap").then().statusCode(OK).body(is(TestApi.body(1)));

        RestAssured.get("/over-cap").then().statusCode(OK).body(is(TestApi.body(2)));
    }
}
