package org.acme.edgy.runtime.builtins.requests;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.INTERNAL_SERVER_ERROR;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

class RequestHeaderModifierTest {
    private static final String HEADER_NAME_TO_BE_CHANGED = "X-Test";
    private static final String HEADER_NAME_NOT_TO_BE_CHANGED = "X-NotThere";

    private static final String ORIGINAL_HEADER_VALUE = "original";
    private static final String MODIFIED_HEADER_VALUE = "changed";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/modify-header",
                            Origin.of("origin-1", "http://localhost:8081/test/modify-header"), PathMode.FIXED)
                                    .addRequestTransformer(new RequestHeaderModifier(
                                            HEADER_NAME_TO_BE_CHANGED, MODIFIED_HEADER_VALUE)))
                    .addRoute(new Route("/no-header",
                            Origin.of("origin-2", "http://localhost:8081/test/no-header"), PathMode.FIXED)
                                    .addRequestTransformer(new RequestHeaderModifier(
                                            HEADER_NAME_TO_BE_CHANGED, MODIFIED_HEADER_VALUE)));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/modify-header")
        @Consumes(TEXT_PLAIN)
        public RestResponse<Void> endpointHeader(
                @HeaderParam(HEADER_NAME_TO_BE_CHANGED) String changedHeader,
                @HeaderParam(HEADER_NAME_NOT_TO_BE_CHANGED) String unchangedHeader) {
            if (!MODIFIED_HEADER_VALUE.equals(changedHeader)
                    || !ORIGINAL_HEADER_VALUE.equals(unchangedHeader)) {
                return RestResponse.status(INTERNAL_SERVER_ERROR);
            }
            return RestResponse.status(OK);
        }

        @GET
        @Path("/no-header")
        @Consumes(TEXT_PLAIN)
        public RestResponse<Void> endpointNoHeader(
                @HeaderParam(HEADER_NAME_TO_BE_CHANGED) String missingHeader,
                @HeaderParam(HEADER_NAME_NOT_TO_BE_CHANGED) String unchangedHeader) {
            if (missingHeader != null || !ORIGINAL_HEADER_VALUE.equals(unchangedHeader)) {
                return RestResponse.status(INTERNAL_SERVER_ERROR);
            }
            return RestResponse.status(OK);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_headerIsModifiedIfExists() {
        RestAssured.given().header(HEADER_NAME_TO_BE_CHANGED, ORIGINAL_HEADER_VALUE)
                .header(HEADER_NAME_NOT_TO_BE_CHANGED, ORIGINAL_HEADER_VALUE).get("/modify-header")
                .then().statusCode(OK);
    }

    @Test
    void test_headerIsNotModifiedIfMissing() {
        RestAssured.given().header(HEADER_NAME_NOT_TO_BE_CHANGED, ORIGINAL_HEADER_VALUE)
                .get("/no-header").then().statusCode(OK);
    }
}
