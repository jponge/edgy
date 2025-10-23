package org.acme.edgy.runtime.builtins.requests;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.hamcrest.Matchers.containsString;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

class RequestJsonArrayToJsonObjectBodyModifierTest {

    private static final String ORIGINAL_JSON = "[\"Lorem\",\"Ipsum\",\"Dolor\",\"Sit\",\"Amet\"]";

    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/object-to-array",
                    Origin.of("http://localhost:8081/test/object-to-array"), PathMode.FIXED)
                            .addRequestTransformer(
                                    new RequestJsonArrayToJsonObjectBodyModifier(json -> {
                                        JsonObject jsonObject = new JsonObject();
                                        for (int i = 0; i < json.size(); i++) {
                                            jsonObject.put(String.valueOf(i + 1), json.getValue(i));
                                        }
                                        return jsonObject;
                                    })))
                    .addRoute(new Route("/array-to-brand-new-object",
                            Origin.of("http://localhost:8081/test/array-to-brand-new-object"),
                            PathMode.FIXED).addRequestTransformer(
                                    new RequestJsonArrayToJsonObjectBodyModifier(json -> {
                                        JsonObject jsonObject = new JsonObject();
                                        jsonObject.put("key", "value");
                                        return jsonObject;
                                    })))
                    .addRoute(new Route("/array-to-empty",
                            Origin.of("http://localhost:8081/test/array-to-empty"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestJsonArrayToJsonObjectBodyModifier(
                                                    json -> null)));
        }
    }

    @Path("/test")
    static class TestApi {
        @POST
        @Path("/object-to-array")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> objectToArrayEndpoint(
                @HeaderParam(CONTENT_LENGTH) int contentLength, String body) {
            final String expectedBody =
                    "{\"1\":\"Lorem\",\"2\":\"Ipsum\",\"3\":\"Dolor\",\"4\":\"Sit\",\"5\":\"Amet\"}";
            if (!body.equals(expectedBody) || contentLength != expectedBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }

        @POST
        @Path("/array-to-brand-new-object")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> arrayToBrandNewObjectEndpoint(
                @HeaderParam(CONTENT_LENGTH) int contentLength, String body) {
            final String expectedBody = "{\"key\":\"value\"}";
            if (!body.equals(expectedBody) || contentLength != expectedBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }

        @POST
        @Path("/array-to-empty")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> arrayToEmptyEndpoint(
                @HeaderParam(CONTENT_LENGTH) int contentLength, String body) {
            if (!body.isEmpty() || contentLength != 0) {
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
    void test_jsonArraytoJsonObject() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/object-to-array").then().statusCode(OK);
        // without previous body (maps to empty JsonArray)
        RestAssured.given().contentType(APPLICATION_JSON).post("/array-to-brand-new-object").then()
                .statusCode(OK);
        // with previous body
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/array-to-brand-new-object").then().statusCode(OK);
    }

    @Test
    void test_jsonArrayToEmpty() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/array-to-empty").then().statusCode(OK);
    }

    @Test
    void test_invalidJsonArray() {
        RestAssured.given().contentType(APPLICATION_JSON).body("some text").post("/object-to-array")
                .then().statusCode(BAD_REQUEST).body(containsString("JSON"));
    }
}
