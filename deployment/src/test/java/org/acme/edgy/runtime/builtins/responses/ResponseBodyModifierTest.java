package org.acme.edgy.runtime.builtins.responses;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

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

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;

class ResponseBodyModifierTest {

    private static final String ORIGINAL_BODY = "original";
    private static final String MODIFIED_BODY = "modified body";

    @ApplicationScoped
    static class RoutingProvider {

        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/modify-body",
                            Origin.of("origin-1", "http://localhost:8081/test/modify-body"), PathMode.FIXED)
                                    .addResponseTransformer(new ResponseBodyModifier(
                                            Body.body(Buffer.buffer(MODIFIED_BODY)))))
                    .addRoute(new Route("/modify-null",
                            Origin.of("origin-2", "http://localhost:8081/test/modify-null"), PathMode.FIXED)
                                    .addResponseTransformer(new ResponseBodyModifier((Body) null)));
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/modify-body")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> endpointBody() {
            return RestResponse.ok(ORIGINAL_BODY);
        }

        @GET
        @Path("/modify-null")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> endpointNull() {
            return RestResponse.ok(ORIGINAL_BODY);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_responseBodyIsModifiedByBodyParam() {
        RestAssured.given().get("/modify-body").then().statusCode(OK).body(is(MODIFIED_BODY)).and()
                .header(CONTENT_LENGTH, String.valueOf(MODIFIED_BODY.length()));
    }

    @Test
    void test_responseBodyIsSetToNull() {
        RestAssured.given().get("/modify-null").then().statusCode(OK).body(is(emptyOrNullString()))
                .and().header(CONTENT_LENGTH, "0");
    }
}
