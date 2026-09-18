package org.acme.edgy.test.cache;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;

class EdgyCacheMetricsTest {

    private static final String CACHEABLE = "public, max-age=60";

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/metered",
                            Origin.of("metered", "http://localhost:8081/test/metered")))
                    .addRoute(new Route("/uncached",
                            Origin.of("uncached", "http://localhost:8081/test/uncached")))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        static final AtomicInteger meteredHits = new AtomicInteger();

        @GET
        @Path("/metered")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> metered() {
            return RestResponse.ResponseBuilder
                    .ok("metered-" + meteredHits.incrementAndGet(), TEXT_PLAIN)
                    .header("Cache-Control", CACHEABLE)
                    .build();
        }

        @GET
        @Path("/uncached")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public String uncached() {
            return "uncached";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .overrideConfigKey("edgy.origin.metered.cache.enabled", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-micrometer-registry-prometheus-deployment",
                    System.getProperty("project.quarkus.version"))));

    @Inject
    MeterRegistry registry;

    private Timer proxyTimer(String route, String cache) {
        return registry.find("edgy.proxy.requests")
                .tag("route", route)
                .tag("cache", cache)
                .timer();
    }

    @Test
    void firstRequestIsTaggedMissAndTheSecondHit() {
        given().get("/metered").then().statusCode(OK).body(is("metered-1"));
        given().get("/metered").then().statusCode(OK).body(is("metered-1"));

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(proxyTimer("/metered", "MISS")).isNotNull()
                    .extracting(Timer::count).isEqualTo(1L);
            assertThat(proxyTimer("/metered", "HIT")).isNotNull()
                    .extracting(Timer::count).isEqualTo(1L);
        });
    }

    @Test
    void anOriginWithoutACacheIsTaggedNone() {
        given().get("/uncached").then().statusCode(OK).body(is("uncached"));

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(proxyTimer("/uncached", "NONE")).isNotNull()
                    .extracting(Timer::count).isEqualTo(1L);
        });
    }
}
