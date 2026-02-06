package org.acme.edgy.runtime.builtins.requests;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.containsString;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

import jakarta.enterprise.context.ApplicationScoped;
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
import io.vertx.core.json.JsonArray;

class RequestJsonObjectToJsonArrayBodyModifierTest {

    private static final String ORIGINAL_JSON = "{\"1\":\"Lorem\",\"2\":\"Ipsum\",\"3\":\"Dolor\"}";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/object-to-array",
                    Origin.of("origin-1", "http://localhost:8081/test/object-to-array"), PathMode.FIXED)
                            .addRequestTransformer(
                                    new RequestJsonObjectToJsonArrayBodyModifier(json -> {
                                        JsonArray jsonArray = new JsonArray();
                                        // Extract values in order
                                        json.fieldNames().stream().sorted()
                                                .forEach(key -> jsonArray.add(json.getValue(key)));
                                        return jsonArray;
                                    })))
                    .addRoute(new Route("/object-to-brand-new-array",
                            Origin.of("origin-2", "http://localhost:8081/test/object-to-brand-new-array"),
                                    PathMode.FIXED).addRequestTransformer(
                                    new RequestJsonObjectToJsonArrayBodyModifier(json -> {
                                        JsonArray jsonArray = new JsonArray();
                                        jsonArray.add("value1");
                                        jsonArray.add("value2");
                                        return jsonArray;
                                    })))
                    .addRoute(new Route("/object-to-empty",
                            Origin.of("origin-3", "http://localhost:8081/test/object-to-empty"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestJsonObjectToJsonArrayBodyModifier(
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
            final String expectedBody = "[\"Lorem\",\"Ipsum\",\"Dolor\"]";
            if (!body.equals(expectedBody) || contentLength != expectedBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }

        @POST
        @Path("/object-to-brand-new-array")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> objectToBrandNewArrayEndpoint(
                @HeaderParam(CONTENT_LENGTH) int contentLength, String body) {
            final String expectedBody = "[\"value1\",\"value2\"]";
            if (!body.equals(expectedBody) || contentLength != expectedBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }

        @POST
        @Path("/object-to-empty")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> objectToEmptyEndpoint(
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
    void test_jsonObjectToJsonArray() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/object-to-array").then().statusCode(OK);
        // without previous body (maps to empty JsonObject)
        RestAssured.given().contentType(APPLICATION_JSON).post("/object-to-brand-new-array").then()
                .statusCode(OK);
        // with previous body
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/object-to-brand-new-array").then().statusCode(OK);
    }

    @Test
    void test_jsonObjectToEmpty() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/object-to-empty").then().statusCode(OK);
    }

    @Test
    void test_invalidJsonObject() {
        RestAssured.given().contentType(APPLICATION_JSON).body("some text").post("/object-to-array")
                .then().statusCode(BAD_REQUEST).body(containsString("JSON"));
    }
}
