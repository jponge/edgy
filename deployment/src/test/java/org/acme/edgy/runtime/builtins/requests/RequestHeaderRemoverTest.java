package org.acme.edgy.runtime.builtins.requests;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

class RequestHeaderRemoverTest {

    private static final String CUSTOM_HEADER_1 = "X-YOLO";
    private static final String CUSTOM_HEADER_2 = "X-ABC";
    private static final String CUSTOM_HEADER_VALUE_1 = "Yolo";
    private static final String CUSTOM_HEADER_VALUE_2 = "abc";

    @ApplicationScoped
    static class RoutingProvider {

        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/hello", Origin.of("origin-1", "http://localhost:8081/test"), PathMode.FIXED)
                            .addRequestTransformer(new RequestHeaderRemover(CUSTOM_HEADER_1)));
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        public RestResponse<Void> endpoint(@RestHeader(CUSTOM_HEADER_1) String yolo, @RestHeader(CUSTOM_HEADER_2) String abc) {
            if (yolo != null || !CUSTOM_HEADER_VALUE_2.equals(abc)) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_removalOfYoloAndAbcHeaders() {
        RestAssured.given()
            .header(CUSTOM_HEADER_1, CUSTOM_HEADER_VALUE_1) // removing
            .header(CUSTOM_HEADER_2, CUSTOM_HEADER_VALUE_2)
            .get("/hello")
            .then()
            .statusCode(OK);
    }
}