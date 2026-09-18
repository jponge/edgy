package org.acme.edgy.test.cache;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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
import io.restassured.config.DecoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;

class EdgyOriginCacheVaryTest {

    private static final String CACHEABLE = "public, max-age=60";

    // stops RestAssured from adding its own Accept-Encoding: gzip,deflate
    private static final RestAssuredConfig noDecoders = RestAssuredConfig.config()
            .decoderConfig(DecoderConfig.decoderConfig().noContentDecoders());

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/vary",
                            Origin.of("vary", "http://localhost:8081/test/vary")))
                    .addRoute(new Route("/vary-star",
                            Origin.of("vary-star", "http://localhost:8081/test/vary-star")))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        static final AtomicInteger varyHits = new AtomicInteger();
        static final AtomicInteger varyStarHits = new AtomicInteger();

        @GET
        @Path("/vary")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> vary(@HeaderParam("Accept-Encoding") String acceptEncoding) {
            String body = acceptEncoding + "-" + varyHits.incrementAndGet();
            return RestResponse.ResponseBuilder.ok(body, TEXT_PLAIN)
                    .header("Cache-Control", CACHEABLE)
                    .header("Vary", "Accept-Encoding")
                    .build();
        }

        @GET
        @Path("/vary-star")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> varyStar() {
            return RestResponse.ResponseBuilder
                    .ok("vary-star-" + varyStarHits.incrementAndGet(), TEXT_PLAIN)
                    .header("Cache-Control", CACHEABLE)
                    .header("Vary", "*")
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .overrideConfigKey("edgy.origin.vary.cache.enabled", "true")
            .overrideConfigKey("edgy.origin.vary-star.cache.enabled", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    private static RequestSpecification withEncoding(String acceptEncoding) {
        return RestAssured.given()
                .config(noDecoders)
                .header("Accept-Encoding", acceptEncoding);
    }

    @Test
    void variantsAreCachedSeparately() {
        // first request: the vary fields are not known yet, so this is a miss and
        // the response is stored under the gzip variant
        withEncoding("gzip").get("/vary")
                .then()
                .statusCode(OK)
                .body(is("gzip-1"));

        // a different variant of the same URI reaches the origin
        withEncoding("identity").get("/vary")
                .then()
                .statusCode(OK)
                .body(is("identity-2"));

        // back to the first variant, served from cache
        withEncoding("gzip").get("/vary")
                .then()
                .statusCode(OK)
                .body(is("gzip-1"));
    }

    @Test
    void varyStarIsNeverCached() {
        RestAssured.get("/vary-star").then().statusCode(OK).body(is("vary-star-1"));
        RestAssured.get("/vary-star").then().statusCode(OK).body(is("vary-star-2"));
    }
}
