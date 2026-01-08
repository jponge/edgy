package org.acme.edgy.runtime.builtins.responses;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.nullValue;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

class ResponseHeaderRemoverTest {

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