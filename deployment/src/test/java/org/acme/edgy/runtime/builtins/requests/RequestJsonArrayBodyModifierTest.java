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

class RequestJsonArrayBodyModifierTest {

    private static final String ORIGINAL_JSON = "[\"Lorem\",\"Ipsum\",\"Dolor\",\"Sit\",\"Amet\"]";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/remove-element",
                    Origin.of("origin-1", "http://localhost:8081/test/remove-element"), PathMode.FIXED)
                            .addRequestTransformer(new RequestJsonArrayBodyModifier(json -> {
                                json.remove(1); // Remove "Ipsum"
                                return json;
                            })))
                    .addRoute(new Route("/modify-element",
                            Origin.of("origin-2", "http://localhost:8081/test/modify-element"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestJsonArrayBodyModifier(json -> {
                                                json.set(0, "Changed");
                                                return json;
                                            })))
                    .addRoute(new Route("/add-element",
                            Origin.of("origin-3", "http://localhost:8081/test/add-element"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestJsonArrayBodyModifier(json -> {
                                                json.add("NewElement");
                                                return json;
                                            })))
                    .addRoute(new Route("/set-null-dynamic",
                            Origin.of("origin-4", "http://localhost:8081/test/set-null-dynamic"),
                                    PathMode.FIXED).addRequestTransformer(
                                    new RequestJsonArrayBodyModifier(json -> null)))
                    .addRoute(new Route("/set-null-static",
                            Origin.of("origin-5", "http://localhost:8081/test/set-null-static"), PathMode.FIXED)
                                    .addRequestTransformer(
                                            new RequestJsonArrayBodyModifier((JsonArray) null)))
                    .addRoute(new Route(
                            "/replace-full", Origin.of("origin-6", "http://localhost:8081/test/replace-full"),
                                    PathMode.FIXED).addRequestTransformer(
                                    new RequestJsonArrayBodyModifier(new JsonArray().add(1).add(2)
                                            .add(new JsonObject().put("key", "value")))));
        }
    }

    @Path("/test")
    static class TestApi {
        @POST
        @Path("/remove-element")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> removeElementEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            final String expectedRequestBody = "[\"Lorem\",\"Dolor\",\"Sit\",\"Amet\"]";
            if (!expectedRequestBody.equals(body)
                    || contentLength != expectedRequestBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.status(OK);
        }

        @POST
        @Path("/modify-element")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> modifyElementEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            final String expectedRequestBody = "[\"Changed\",\"Ipsum\",\"Dolor\",\"Sit\",\"Amet\"]";
            if (!expectedRequestBody.equals(body)
                    || contentLength != expectedRequestBody.length()) {
                return RestResponse.serverError();
            }
            return RestResponse.status(OK);
        }

        @POST
        @Path("/add-element")
        @Consumes(APPLICATION_JSON)
        public RestResponse<Void> addElementEndpoint(String body,
                @HeaderParam(CONTENT_LENGTH) int contentLength) {
            final String expectedRequestBody =
                    "[\"Lorem\",\"Ipsum\",\"Dolor\",\"Sit\",\"Amet\",\"NewElement\"]";
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
            String expected = "[1,2,{\"key\":\"value\"}]";
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
    void test_removeJSONElement() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/remove-element").then().statusCode(OK);
    }

    @Test
    void test_modifyJSONElement() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON)
                .post("/modify-element").then().statusCode(OK);
    }

    @Test
    void test_addJSONElement() {
        RestAssured.given().contentType(APPLICATION_JSON).body(ORIGINAL_JSON).post("/add-element")
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
        RestAssured.given().contentType(APPLICATION_JSON).body("[\"some\",\"value\"]")
                .post("/replace-full").then().statusCode(OK);
    }

    @Test
    void test_dynamicFailsDueToInvalidJSONBody() {
        RestAssured.given().contentType(APPLICATION_JSON).body("invalid json")
                .post("/modify-element").then().statusCode(BAD_REQUEST).and()
                .body(containsString("JSON"));
    }
}
