package org.acme.edgy.runtime.builtins.transformers.responses;

import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.nullValue;

import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

class ResponseHeaderRemoverTest {

    private static final String CUSTOM_HEADER_1 = "X-YOLO";
    private static final String CUSTOM_HEADER_2 = "X-ABC";
    private static final String CUSTOM_HEADER_VALUE_1 = "Yolo";
    private static final String CUSTOM_HEADER_VALUE_2 = "abc";

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/hello", Origin.of("origin-1", "http://localhost:8081/test"))
                            .addResponseTransformer(new ResponseHeaderRemover(CUSTOM_HEADER_1)));
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        public RestResponse<Void> endpoint() {
            return RestResponse.ResponseBuilder.<Void>ok()
                .header(CUSTOM_HEADER_1, CUSTOM_HEADER_VALUE_1) // removing
                .header(CUSTOM_HEADER_2, CUSTOM_HEADER_VALUE_2)
                .build();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_removalOfYoloHeader() {
        RestAssured.given()
            .get("/hello")
            .then()
            .statusCode(OK)
            .header(CUSTOM_HEADER_1, nullValue())
            .header(CUSTOM_HEADER_2, CUSTOM_HEADER_VALUE_2);
    }
}