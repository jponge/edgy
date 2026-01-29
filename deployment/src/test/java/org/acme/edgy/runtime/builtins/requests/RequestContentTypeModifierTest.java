package org.acme.edgy.runtime.builtins.requests;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
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

class RequestContentTypeModifierTest {
    private record Payload(String hello) {
    }

    private static final Payload payloadObject = new Payload("world");
    private static final String TEXTIFIED_JSON_PAYLOAD = "{\"hello\":\"world\"}";

    private static final String COPYRIGHT = "©";

    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/json-to-plain",
                            Origin.of("origin-1", "http://localhost:8081/test/json-to-plain"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestContentTypeModifier(TEXT_PLAIN)))
                    .addRoute(new Route("/plain-to-json",
                            Origin.of("origin-2", "http://localhost:8081/test/plain-to-json"), PathMode.FIXED)
                                    .addRequestTransformer(
                                    new RequestContentTypeModifier(APPLICATION_JSON)))
                    .addRoute(new Route("/charset-transform",
                            Origin.of("origin-3", "http://localhost:8081/test/charset-check-encoded"), PathMode.FIXED)
                            .addRequestTransformer(
                                    new RequestContentTypeModifier("text/plain; charset=UTF-16BE")));
        }
    }

    @Path("/test")
    static class TestApi {
        @POST
        @Path("/json-to-plain")
        @Consumes(TEXT_PLAIN)
        public RestResponse<Void> endpoint(String body) {
            if (!TEXTIFIED_JSON_PAYLOAD.equals(body)) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }

        @POST
        @Path("/plain-to-json")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> plainToJson(Payload body) {
            if (!payloadObject.equals(body)) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }

        @POST
        @Path("/charset-check-encoded")
        @Consumes("text/plain; charset=UTF-16BE")
        public RestResponse<Void> charsetCheckEncoded(@HeaderParam(CONTENT_TYPE) String contentType,
                @HeaderParam(CONTENT_LENGTH) Long contentLength, byte[] body) {
            if (!contentType.contains(StandardCharsets.UTF_16BE.name()) || contentLength != 2 || body.length != 2) {
                return RestResponse.serverError();
            }

            String decodedBody = new String(body, StandardCharsets.UTF_16BE);
            if (!COPYRIGHT.equals(decodedBody)) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_contentReplacesJsonToPlain() {
        RestAssured.given().contentType(APPLICATION_JSON).body(payloadObject).post("/json-to-plain")
                .then().statusCode(OK);
    }

    @Test
    void test_contentReplacesPlainToJson() {
        RestAssured.given().contentType(TEXT_PLAIN).body(TEXTIFIED_JSON_PAYLOAD)
                .post("/plain-to-json").then().statusCode(OK);
    }

    @Test
    void test_contentLengthUpdatedAfterTranscode() {
        assertEquals(1, COPYRIGHT.getBytes(StandardCharsets.ISO_8859_1).length);
        assertEquals(2, COPYRIGHT.getBytes(StandardCharsets.UTF_16BE).length);

        RestAssured.given()
                .contentType("text/plain; charset=ISO-8859-1")
                .body(COPYRIGHT)
                .post("/charset-transform")
                .then()
                .statusCode(OK);
    }
}