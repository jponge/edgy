package org.acme.edgy.test.tracing;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

class EdgyTracingDisabledTest {

    static final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class, SpanExporterProducer.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry-deployment",
                    System.getProperty("project.quarkus.version"))))
            .overrideConfigKey("edgy.tracing.enabled", "false");

    @Test
    void test_proxyWorksWithoutEdgyAttributes() {
        given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello from origin!"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<SpanData> spans = spanExporter.getFinishedSpanItems();

            SpanData serverSpan = spans.stream()
                    .filter(s -> s.getKind() == SpanKind.SERVER && s.getParentSpanId().equals("0000000000000000"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No root SERVER span found in: " + spans));

            Attributes attrs = serverSpan.getAttributes();
            assertNull(attrs.get(AttributeKey.stringKey("edgy.origin.url")));
            assertNull(attrs.get(AttributeKey.stringKey("edgy.origin.id")));
            assertNull(attrs.get(AttributeKey.stringKey("edgy.route")));
        });
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/hello", Origin.of("origin-1", "http://localhost:8081/test/hello")));
        }
    }

    @Path("/test/hello")
    static class TestApi {

        @GET
        public String hello() {
            return "Hello from origin!";
        }
    }

    @ApplicationScoped
    static class SpanExporterProducer {

        @Produces
        @Singleton
        public InMemorySpanExporter spanExporter() {
            return spanExporter;
        }
    }
}
