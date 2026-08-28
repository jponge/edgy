package org.acme.edgy.test.method;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpMethod;

class RouteMethodOverrideTest {

    static final int WIREMOCK_PORT = 9091;
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT).gzipDisabled(true));
        wireMockServer.start();
        wireMockServer.stubFor(put(urlPathEqualTo("/test/post-to-put"))
                .willReturn(aResponse().withBody("put-ok")));
        wireMockServer.stubFor(get(urlPathEqualTo("/test/post-to-get"))
                .willReturn(aResponse().withBody("get-ok")));
        wireMockServer.stubFor(get(urlPathEqualTo("/test/post-to-get-keep-body"))
                .willReturn(aResponse().withBody("get-keep-ok")));
        wireMockServer.stubFor(delete(urlPathEqualTo("/test/post-to-delete"))
                .willReturn(aResponse().withBody("delete-ok")));
        wireMockServer.stubFor(get(urlPathEqualTo("/test/mapper"))
                .willReturn(aResponse().withBody("mapper-ok")));
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetRequests();
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    // POST -> PUT: body should be forwarded (PUT has body semantics)
                    .addRoute(new Route("/post-to-put",
                            Origin.of("o1", "http://localhost:9091/test/post-to-put"))
                            .setMethod(HttpMethod.PUT))
                    // POST -> GET: body should be dropped (GET is bodyless, keepBody=false)
                    .addRoute(new Route("/post-to-get",
                            Origin.of("o2", "http://localhost:9091/test/post-to-get"))
                            .setMethod(HttpMethod.GET))
                    // POST -> GET + keepBody=true: body should be forwarded despite GET
                    .addRoute(new Route("/post-to-get-keep-body",
                            Origin.of("o3", "http://localhost:9091/test/post-to-get-keep-body"))
                            .setMethod(HttpMethod.GET)
                            .setKeepBody(true))
                    // POST -> DELETE: body should be dropped (DELETE is bodyless)
                    .addRoute(new Route("/post-to-delete",
                            Origin.of("o4", "http://localhost:9091/test/post-to-delete"))
                            .setMethod(HttpMethod.DELETE))
                    .build();
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class));

    @Test
    void postToPutForwardsBody() {
        RestAssured.given()
                .body("payload")
                .post("/post-to-put")
                .then()
                .statusCode(OK);

        wireMockServer.verify(putRequestedFor(urlPathEqualTo("/test/post-to-put"))
                .withRequestBody(equalTo("payload")));
    }

    @Test
    void postToGetDropsBody() {
        RestAssured.given()
                .body("payload")
                .post("/post-to-get")
                .then()
                .statusCode(OK);

        LoggedRequest request = wireMockServer
                .findAll(getRequestedFor(urlPathEqualTo("/test/post-to-get")))
                .get(0);
        assertThat(request.getBody()).isEmpty();
    }

    @Test
    void postToGetKeepBodyForwardsBody() {
        RestAssured.given()
                .body("payload")
                .post("/post-to-get-keep-body")
                .then()
                .statusCode(OK);

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/test/post-to-get-keep-body"))
                .withRequestBody(equalTo("payload")));
    }

    @Test
    void postToDeleteDropsBody() {
        RestAssured.given()
                .body("payload")
                .post("/post-to-delete")
                .then()
                .statusCode(OK);

        LoggedRequest request = wireMockServer
                .findAll(deleteRequestedFor(urlPathEqualTo("/test/post-to-delete")))
                .get(0);
        assertThat(request.getBody()).isEmpty();
    }
}
