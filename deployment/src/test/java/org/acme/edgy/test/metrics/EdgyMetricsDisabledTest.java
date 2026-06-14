package org.acme.edgy.test.metrics;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;

class EdgyMetricsDisabledTest {

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-micrometer-registry-prometheus-deployment",
                    System.getProperty("project.quarkus.version"))))
            .overrideConfigKey("edgy.metrics.enabled", "false");

    @Inject
    MeterRegistry registry;

    @Test
    void test_noEdgyMetricsWhenDisabled() {
        given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello from origin!"));

        assertNull(registry.find("edgy.proxy.requests").timer(),
                "No edgy proxy timer should be registered when metrics are disabled");
        assertNull(registry.find("edgy.routes.count").gauge(),
                "No route count gauge should be registered when metrics are disabled");
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/hello", Origin.of("origin-1", "http://localhost:8081/test/hello"))).build();
        }
    }

    @Path("/test/hello")
    static class TestApi {

        @GET
        public String hello() {
            return "Hello from origin!";
        }
    }
}
