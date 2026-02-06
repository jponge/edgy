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
import io.vertx.core.json.JsonObject;

class RequestJsonObjectBodyModifierTest {

    private static final String ORIGINAL_JSON =
            "{\"1\":\"Lorem\",\"2\":\"Ipsum\",\"3\":[\"Dolor\",\"Sit\",\"Amet\"]}";


    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/remove-field",
                    Origin.of("origin-1", "http://localhost:8081/test/remove-field"), PathMode.FIXED)
                            .addRequestTransformer(new RequestJsonObjectBodyModifier(json -> {
                                json.remove("2");
                                return json;
                            })))
                    .addRoute(new Route("/modify-field",
                            Origin.of("origin-2", "http://localhost:8081/test/modify-field"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestJsonObjectBodyModifier(json -> {
                                                json.put("1", "Changed");
                                                return json;
                                            })))
                    .addRoute(new Route("/add-field",
                            Origin.of("origin-3", "http://localhost:8081/test/add-field"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestJsonObjectBodyModifier(json -> {
                                                json.put("4", "NewVal");
                                                return json;
                                            })))
                    .addRoute(new Route("/set-null-dynamic",
                            Origin.of("origin-4", "http://localhost:8081/test/set-null-dynamic"),
                                    PathMode.FIXED).addRequestTransformer(
                                    new RequestJsonObjectBodyModifier(json -> null)))
                    .addRoute(new Route("/set-null-static",
                            Origin.of("origin-5", "http://localhost:8081/test/set-null-static"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestJsonObjectBodyModifier((JsonObject) null)))
                    .addRoute(new Route("/replace-full",
                            Origin.of("origin-6", "http://localhost:8081/test/replace-full"), PathMode.FIXED)
                                    .addRequestTransformer(new RequestJsonObjectBodyModifier(
                                            new JsonObject().put("replaced", "yes").put("arr",
                                                    new JsonArray().add(1).add(2)))));
        }
    }


    @Path("/test")
    static class TestApi {
        @POST
        @Path("/remove-field")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> removeFieldEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            final String expectedRequestBody =
                    "{\"1\":\"Lorem\",\"3\":[\"Dolor\",\"Sit\",\"Amet\"]}";
            if (!expectedRequestBody.equals(body)
                    || contentLength != expectedRequestBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.status(OK);
        }

        @POST
        @Path("/modify-field")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> modifyFieldEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            final String expectedRequestBody =
                    "{\"1\":\"Changed\",\"2\":\"Ipsum\",\"3\":[\"Dolor\",\"Sit\",\"Amet\"]}";
            if (!expectedRequestBody.equals(body)
                    || contentLength != expectedRequestBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.status(OK);
        }

        @POST
        @Path("/add-field")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> addFieldEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            final String expectedRequestBody =
                    "{\"1\":\"Lorem\",\"2\":\"Ipsum\",\"3\":[\"Dolor\",\"Sit\",\"Amet\"],\"4\":\"NewVal\"}";
            if (!expectedRequestBody.equals(body)
                    || contentLength != expectedRequestBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.status(OK);
        }

        @POST
        @Path("/set-null-dynamic")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> setNullDynamicEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            // body should be empty when transformer returns null
            if (body != null && !body.isEmpty() || contentLength != 0) {
                return RestResponse.serverError();
            }
            return RestResponse.status(OK);
        }

        @POST
        @Path("/set-null-static")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> setNullStaticEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            if (body != null && !body.isEmpty() || contentLength != 0) {
                return RestResponse.serverError();
            }
            return RestResponse.status(OK);
        }

        @POST
        @Path("/replace-full")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> replaceFullEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            String expected = "{\"replaced\":\"yes\",\"arr\":[1,2]}";
            if (!expected.equals(body) || contentLength != expected.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.status(OK);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_removeJSONField() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON).post("/remove-field")
                .then().statusCode(OK);
    }

    @Test
    void test_modifyJSONField() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON).post("/modify-field")
                .then().statusCode(OK);
    }

    @Test
    void test_addJSONField() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON).post("/add-field")
                .then().statusCode(OK);
    }

    @Test
    void test_setNullDynamic() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/set-null-dynamic").then().statusCode(OK);
    }

    @Test
    void test_setNullStatic() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/set-null-static").then().statusCode(OK);
    }

    @Test
    void test_replaceFull() {
        RestAssured.given().contentType(APPLICATION_JSON).body("{\"some\":\"value\"}")
                .post("/replace-full").then().statusCode(OK);
    }

    @Test
    void test_dynamicFailsDueToInvalidJSONBody() {
        RestAssured.given().contentType(APPLICATION_JSON).body("invalid json").post("/modify-field")
                .then().statusCode(BAD_REQUEST).and().body(containsString("JSON"));
    }
}
