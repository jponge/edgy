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
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

class ResponseJsonArrayToJsonObjectBodyModifierTest {

    private static final String ORIGINAL_JSON = "[\"Lorem\",\"Ipsum\",\"Dolor\",\"Sit\",\"Amet\"]";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/array-to-object",
                    Origin.of("http://localhost:8081/test/array-to-object"), PathMode.FIXED)
                            .addResponseTransformer(
                                    new ResponseJsonArrayToJsonObjectBodyModifier(json -> {
                                        JsonObject jsonObject = new JsonObject();
                                        for (int i = 0; i < json.size(); i++) {
                                            jsonObject.put(String.valueOf(i + 1), json.getValue(i));
                                        }
                                        return jsonObject;
                                    })))
                    .addRoute(new Route("/array-to-brand-new-object",
                            Origin.of("http://localhost:8081/test/array-to-brand-new-object"),
                            PathMode.FIXED).addResponseTransformer(
                                    new ResponseJsonArrayToJsonObjectBodyModifier(json -> {
                                        JsonObject jsonObject = new JsonObject();
                                        jsonObject.put("key", "value");
                                        return jsonObject;
                                    })))
                    .addRoute(new Route("/array-to-empty",
                            Origin.of("http://localhost:8081/test/array-to-empty"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJsonArrayToJsonObjectBodyModifier(
                                                    json -> null)))
                    .addRoute(new Route("/invalid-json",
                            Origin.of("http://localhost:8081/test/invalid-json"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJsonArrayToJsonObjectBodyModifier(json -> {
                                                // Just transform to object for invalid JSON test
                                                JsonObject obj = new JsonObject();
                                                obj.put("data", json);
                                                return obj;
                                            })));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/array-to-object")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String arrayToObjectEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/array-to-brand-new-object")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String arrayToBrandNewObjectEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/array-to-empty")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String arrayToEmptyEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/invalid-json")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public String invalidJson() {
            return "some text";
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_jsonArrayToJsonObject() {
        final String expectedResponseBody =
                "{\"1\":\"Lorem\",\"2\":\"Ipsum\",\"3\":\"Dolor\",\"4\":\"Sit\",\"5\":\"Amet\"}";
        RestAssured.given().contentType(APPLICATION_JSON).get("/array-to-object").then()
                .statusCode(OK).body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_arrayToBrandNewObject() {
        final String expectedResponseBody = "{\"key\":\"value\"}";
        RestAssured.given().contentType(APPLICATION_JSON).get("/array-to-brand-new-object").then()
                .statusCode(OK).body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_arrayToEmpty() {
        RestAssured.given().contentType(APPLICATION_JSON).get("/array-to-empty").then()
                .statusCode(OK).body(is(emptyOrNullString())).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is("0"));
    }

    @Test
    void test_invalidJsonArray() {
        RestAssured.given().contentType(APPLICATION_JSON).get("/invalid-json").then()
                .statusCode(BAD_REQUEST).body(containsString("JSON"));
    }
}
