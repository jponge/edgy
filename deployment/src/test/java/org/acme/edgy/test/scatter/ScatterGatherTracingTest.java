package org.acme.edgy.test.scatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
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

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

class ScatterGatherTracingTest {

    static final int WIREMOCK_PORT = 9091;
    private static WireMockServer wireMockServer;

    static final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT).gzipDisabled(true));
        wireMockServer.start();
        wireMockServer.stubFor(get(urlPathEqualTo("/test/t1"))
                .willReturn(aResponse().withBody("t1")));
        wireMockServer.stubFor(get(urlPathEqualTo("/test/t2"))
                .willReturn(aResponse().withBody("t2")));
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, SpanExporterProducer.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry-deployment",
                    System.getProperty("project.quarkus.version"))));

    @Test
    void scatterTracingEnrichesServerSpan() {
        given()
                .get("/scatter-traced")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("t1|t2"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var spans = spanExporter.getFinishedSpanItems();

            // server span has scatter-level attributes
            assertThat(spans)
                    .filteredOn(s -> s.getKind() == SpanKind.SERVER
                            && SpanId.getInvalid().equals(s.getParentSpanId()))
                    .first()
                    .satisfies(serverSpan -> {
                        var attrs = serverSpan.getAttributes();
                        assertThat(attrs.get(AttributeKey.stringKey("edgy.scatter.route")))
                                .isEqualTo("/scatter-traced");
                        assertThat(attrs.get(AttributeKey.longKey("edgy.scatter.leg.count")))
                                .isEqualTo(2L);
                        assertThat(attrs.get(AttributeKey.stringKey("edgy.scatter.origins")))
                                .isEqualTo("t1,t2");
                        assertThat(attrs.get(
                                AttributeKey.longKey("edgy.scatter.legs.succeeded")))
                                .isEqualTo(2L);
                        assertThat(attrs.get(AttributeKey.longKey("edgy.scatter.legs.failed")))
                                .isZero();

                        // child spans per leg
                        var legSpans = spans.stream()
                                .filter(s -> s.getParentSpanId()
                                        .equals(serverSpan.getSpanId()))
                                .filter(s -> s.getName()
                                        .startsWith("edgy scatter leg:"))
                                .toList();
                        assertThat(legSpans).hasSize(2);
                        assertThat(legSpans).extracting(s -> s.getAttributes()
                                .get(AttributeKey.stringKey("edgy.origin.id")))
                                .containsExactlyInAnyOrder("t1", "t2");
                        assertThat(legSpans).allSatisfy(s -> assertThat(
                                s.getAttributes()
                                        .get(AttributeKey.longKey(
                                                "edgy.leg.status")))
                                .isEqualTo(200L));

                        // CLIENT spans are children of their respective leg spans
                        for (var legSpan : legSpans) {
                            var clientSpans = spans.stream()
                                    .filter(s -> s.getKind() == SpanKind.CLIENT)
                                    .filter(s -> s.getParentSpanId()
                                            .equals(legSpan.getSpanId()))
                                    .toList();
                            assertThat(clientSpans)
                                    .as("CLIENT span should be child of leg span %s",
                                            legSpan.getName())
                                    .hasSize(1);
                        }
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
                    .addScatterRoute(new ScatterRoute("/scatter-traced",
                            responses -> {
                                String result = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(result));
                            },
                            new Leg(Origin.of("t1", "http://localhost:9091/test/t1")),
                            new Leg(Origin.of("t2", "http://localhost:9091/test/t2"))))
                    .build();
        }
    }
}
