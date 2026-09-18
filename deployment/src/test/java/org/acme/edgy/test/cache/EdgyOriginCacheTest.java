package org.acme.edgy.test.cache;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.acme.edgy.runtime.api.utils.StatusCode.INTERNAL_SERVER_ERROR;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

class EdgyOriginCacheTest {

    private static final String CACHEABLE = "public, max-age=60";

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/cacheable",
                            Origin.of("cacheable", "http://localhost:8081/test/cacheable")))
                    .addRoute(new Route("/post",
                            Origin.of("post", "http://localhost:8081/test/post")))
                    .addRoute(new Route("/no-directives",
                            Origin.of("no-directives", "http://localhost:8081/test/no-directives")))
                    .addRoute(new Route("/disabled",
                            Origin.of("disabled", "http://localhost:8081/test/disabled")))
                    .addRoute(new Route("/error",
                            Origin.of("error", "http://localhost:8081/test/error")))
                    .addRoute(new Route("/bypass",
                            Origin.of("bypass", "http://localhost:8081/test/bypass")))
                    .addRoute(new Route("/expiring",
                            Origin.of("expiring", "http://localhost:8081/test/expiring")))
                    .addRoute(new Route("/set-cookie",
                            Origin.of("set-cookie", "http://localhost:8081/test/set-cookie")))
                    .addRoute(new Route("/max-age-zero",
                            Origin.of("max-age-zero", "http://localhost:8081/test/max-age-zero")))
                    .addRoute(new Route("/oversized",
                            Origin.of("oversized", "http://localhost:8081/test/oversized")))
                    .build();
        }
    }

    // each endpoint counts its own invocations into the body, so the body alone
    // says whether the origin was reached
    @Path("/test")
    static class TestApi {

        static final AtomicInteger cacheableHits = new AtomicInteger();
        static final AtomicInteger postHits = new AtomicInteger();
        static final AtomicInteger noDirectivesHits = new AtomicInteger();
        static final AtomicInteger disabledHits = new AtomicInteger();
        static final AtomicInteger errorHits = new AtomicInteger();
        static final AtomicInteger bypassHits = new AtomicInteger();
        static final AtomicInteger expiringHits = new AtomicInteger();
        static final AtomicInteger setCookieHits = new AtomicInteger();
        static final AtomicInteger maxAgeZeroHits = new AtomicInteger();
        static final AtomicInteger oversizedHits = new AtomicInteger();

        @GET
        @Path("/cacheable")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> cacheable() {
            return cacheableResponse("cacheable-" + cacheableHits.incrementAndGet());
        }

        @POST
        @Path("/post")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> post() {
            return cacheableResponse("post-" + postHits.incrementAndGet());
        }

        @GET
        @Path("/no-directives")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public String noDirectives() {
            return "no-directives-" + noDirectivesHits.incrementAndGet();
        }

        @GET
        @Path("/disabled")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> disabled() {
            return cacheableResponse("disabled-" + disabledHits.incrementAndGet());
        }

        @GET
        @Path("/error")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> error() {
            return RestResponse.ResponseBuilder.<String>create(INTERNAL_SERVER_ERROR)
                    .entity("error-" + errorHits.incrementAndGet())
                    .type(TEXT_PLAIN)
                    .header("Cache-Control", CACHEABLE)
                    .build();
        }

        @GET
        @Path("/bypass")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> bypass() {
            return cacheableResponse("bypass-" + bypassHits.incrementAndGet());
        }

        @GET
        @Path("/expiring")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> expiring() {
            return RestResponse.ResponseBuilder
                    .ok("expiring-" + expiringHits.incrementAndGet(), TEXT_PLAIN)
                    .header("Cache-Control", "public, max-age=2")
                    .build();
        }

        @GET
        @Path("/set-cookie")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> setCookie() {
            return RestResponse.ResponseBuilder
                    .ok("cookie-" + setCookieHits.incrementAndGet(), TEXT_PLAIN)
                    .header("Cache-Control", CACHEABLE)
                    .header("Set-Cookie", "session=abc123")
                    .build();
        }

        @GET
        @Path("/max-age-zero")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> maxAgeZero() {
            return cacheableResponse("max-age-zero-" + maxAgeZeroHits.incrementAndGet());
        }

        @GET
        @Path("/oversized")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> oversized() {
            String body = "x".repeat(2000) + "-" + oversizedHits.incrementAndGet();
            return cacheableResponse(body);
        }

        private static RestResponse<String> cacheableResponse(String body) {
            return RestResponse.ResponseBuilder.ok(body, TEXT_PLAIN)
                    .header("Cache-Control", CACHEABLE)
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .overrideConfigKey("edgy.origin.cacheable.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.post.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.no-directives.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.error.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.bypass.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.expiring.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.set-cookie.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.max-age-zero.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.oversized.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.oversized.cache.max-entry-size", "1K")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void secondRequestIsServedFromCache() {
        RestAssured.get("/cacheable")
                .then()
                .statusCode(OK)
                .body(is("cacheable-1"));

        // same body means the origin was never reached a second time
        RestAssured.get("/cacheable")
                .then()
                .statusCode(OK)
                .body(is("cacheable-1"))
                .header("Age", notNullValue());
    }

    @Test
    void postIsNeverServedFromCache() {
        RestAssured.post("/post").then().statusCode(OK).body(is("post-1"));
        RestAssured.post("/post").then().statusCode(OK).body(is("post-2"));
    }

    @Test
    void responseWithoutCacheControlIsNotCached() {
        RestAssured.get("/no-directives").then().statusCode(OK).body(is("no-directives-1"));
        RestAssured.get("/no-directives").then().statusCode(OK).body(is("no-directives-2"));
    }

    @Test
    void cacheableResponseIsNotCachedWhenTheOriginHasCachingDisabled() {
        RestAssured.get("/disabled").then().statusCode(OK).body(is("disabled-1"));
        RestAssured.get("/disabled").then().statusCode(OK).body(is("disabled-2"));
    }

    @Test
    void serverErrorIsNotCached() {
        RestAssured.get("/error").then().statusCode(INTERNAL_SERVER_ERROR).body(is("error-1"));
        RestAssured.get("/error").then().statusCode(INTERNAL_SERVER_ERROR).body(is("error-2"));
    }

    @Test
    void requestNoCacheBypassesAWarmEntry() {
        RestAssured.get("/bypass").then().statusCode(OK).body(is("bypass-1"));

        RestAssured.given()
                .header("Cache-Control", "no-cache")
                .get("/bypass")
                .then()
                .statusCode(OK)
                .body(is("bypass-2"));
    }

    // a hit re-enters the interceptor's handleProxyResponse, where the replayed
    // entry looks storable. Re-storing it there refreshes its timestamp, so an
    // entry hit more often than its max-age would never expire - the middle
    // request is what keeps this one warm, and the third is the assertion.
    @Test
    void aWarmEntryStillExpires() throws InterruptedException {
        RestAssured.get("/expiring").then().statusCode(OK).body(is("expiring-1"));

        Thread.sleep(1_100);
        RestAssured.get("/expiring")
                .then()
                .statusCode(OK)
                .body(is("expiring-1"))
                .header("Age", notNullValue());

        Thread.sleep(1_100);
        RestAssured.get("/expiring").then().statusCode(OK).body(is("expiring-2"));
    }

    @Test
    void setCookieResponseIsNotCached() {
        RestAssured.get("/set-cookie").then().statusCode(OK).body(is("cookie-1"));
        RestAssured.get("/set-cookie").then().statusCode(OK).body(is("cookie-2"));
    }

    @Test
    void maxAgeZeroBypassesWarmEntry() {
        RestAssured.get("/max-age-zero").then().statusCode(OK).body(is("max-age-zero-1"));

        RestAssured.given()
                .header("Cache-Control", "max-age=0")
                .get("/max-age-zero")
                .then()
                .statusCode(OK)
                .body(is("max-age-zero-2"));
    }

    @Test
    void oversizedBodyIsNotCached() {
        String firstBody = RestAssured.get("/oversized")
                .then()
                .statusCode(OK)
                .extract().body().asString();

        String secondBody = RestAssured.get("/oversized")
                .then()
                .statusCode(OK)
                .extract().body().asString();

        // different bodies (different counters) prove both reached the origin,
        // and both are complete (2000 x's plus the counter)
        org.assertj.core.api.Assertions.assertThat(firstBody).hasSize(2002).endsWith("-1");
        org.assertj.core.api.Assertions.assertThat(secondBody).hasSize(2002).endsWith("-2");
    }
}
