package org.acme.edgy.test.scatter;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;

class ScatterGatherTracingTest {

    static final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class, SpanExporterProducer.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry-deployment",
                    System.getProperty("project.quarkus.version"))));

    @Test
    void test_scatter_tracing_enriches_server_span() {
        given()
                .get("/scatter-traced")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("t1+t2"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var spans = spanExporter.getFinishedSpanItems();
            assertThat(spans)
                    .filteredOn(s -> s.getKind() == SpanKind.SERVER
                            && SpanId.getInvalid().equals(s.getParentSpanId()))
                    .first()
                    .satisfies(serverSpan -> {
                        var attrs = serverSpan.getAttributes();
                        assertThat(attrs.get(AttributeKey.stringKey("edgy.scatter.route")))
                                .isEqualTo("/scatter-traced");
                    });
        });
    }

    @ApplicationScoped
    static class SpanExporterProducer {

        @Produces
        @Singleton
        public InMemorySpanExporter spanExporter() {
            return spanExporter;
        }
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter-traced")
                            .addLeg(new Leg(Origin.of("t1", "http://localhost:8081/test/t1"))
                                    .setMethod(HttpMethod.GET))
                            .addLeg(new Leg(Origin.of("t2", "http://localhost:8081/test/t2"))
                                    .setMethod(HttpMethod.GET))
                            .setComposer(responses -> {
                                String result = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("+"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            }))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/t1")
        public String t1() {
            return "t1";
        }

        @GET
        @Path("/t2")
        public String t2() {
            return "t2";
        }
    }
}
