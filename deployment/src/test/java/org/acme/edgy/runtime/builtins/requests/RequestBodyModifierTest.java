package org.acme.edgy.runtime.builtins.requests;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.HashMap;
import java.util.Map;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.INTERNAL_SERVER_ERROR;

class RequestBodyModifierTest {

    private static final String ORIGINAL_BODY = "original";
    private static final String MODIFIED_BODY = "modified body";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/modify-body",
                            Origin.of("http://localhost:8081/test/modify-body"), PathMode.FIXED)
                                    .addRequestTransformer(new RequestBodyModifier(
                                            Body.body(Buffer.buffer(MODIFIED_BODY)))))
                    .addRoute(new Route("/modify-null",
                            Origin.of("http://localhost:8081/test/modify-null"), PathMode.FIXED)
                                    .addRequestTransformer(new RequestBodyModifier((Body) null)));
        }
    }

    @Path("/test")
    static class TestApi {

        @POST
        @Path("/modify-body")
        @Consumes(TEXT_PLAIN)
        public RestResponse<Void> endpointBody(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            if (!MODIFIED_BODY.equals(body) || contentLength != MODIFIED_BODY.length()) {
                return RestResponse.status(INTERNAL_SERVER_ERROR);
            }
            return RestResponse.status(OK);
        }

        @POST
        @Path("/modify-null")
        @Consumes(TEXT_PLAIN)
        public RestResponse<Void> endpointNull(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            if (body != null && !body.isEmpty() || contentLength != body.length()) {
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
    void test_bodyIsModifiedByBodyParam() {
        RestAssured.given().body(ORIGINAL_BODY).contentType(TEXT_PLAIN).post("/modify-body").then()
                .statusCode(OK);
    }

    @Test
    void test_bodyIsSetToNull() {
        RestAssured.given().body(ORIGINAL_BODY).contentType(TEXT_PLAIN).post("/modify-null").then()
                .statusCode(OK);
    }
}
