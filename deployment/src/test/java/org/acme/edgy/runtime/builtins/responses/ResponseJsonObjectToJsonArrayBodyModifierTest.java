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

class ResponseJsonObjectToJsonArrayBodyModifierTest {

    private static final String ORIGINAL_JSON = "{\"1\":\"Lorem\",\"2\":\"Ipsum\",\"3\":\"Dolor\"}";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/object-to-array",
                    Origin.of("http://localhost:8081/test/object-to-array"), PathMode.FIXED)
                            .addResponseTransformer(
                                    new ResponseJsonObjectToJsonArrayBodyModifier(json -> {
                                        JsonArray jsonArray = new JsonArray();
                                        // Extract values in order
                                        json.fieldNames().stream().sorted()
                                                .forEach(key -> jsonArray.add(json.getValue(key)));
                                        return jsonArray;
                                    })))
                    .addRoute(new Route("/object-to-brand-new-array",
                            Origin.of("http://localhost:8081/test/object-to-brand-new-array"),
                            PathMode.FIXED).addResponseTransformer(
                                    new ResponseJsonObjectToJsonArrayBodyModifier(json -> {
                                        JsonArray jsonArray = new JsonArray();
                                        jsonArray.add("value1");
                                        jsonArray.add("value2");
                                        return jsonArray;
                                    })))
                    .addRoute(new Route("/object-to-empty",
                            Origin.of("http://localhost:8081/test/object-to-empty"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJsonObjectToJsonArrayBodyModifier(
                                                    json -> null)))
                    .addRoute(new Route("/invalid-json",
                            Origin.of("http://localhost:8081/test/invalid-json"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJsonObjectToJsonArrayBodyModifier(json -> {
                                                // Just transform to array for invalid JSON test
                                                JsonArray arr = new JsonArray();
                                                arr.add(json);
                                                return arr;
                                            })));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/object-to-array")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String objectToArrayEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/object-to-brand-new-array")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String objectToBrandNewArrayEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/object-to-empty")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String objectToEmptyEndpoint() {
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
    void test_jsonObjectToJsonArray() {
        final String expectedResponseBody = "[\"Lorem\",\"Ipsum\",\"Dolor\"]";
        RestAssured.given().contentType(APPLICATION_JSON).get("/object-to-array").then()
                .statusCode(OK).body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_objectToBrandNewArray() {
        final String expectedResponseBody = "[\"value1\",\"value2\"]";
        RestAssured.given().contentType(APPLICATION_JSON).get("/object-to-brand-new-array").then()
                .statusCode(OK).body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_objectToEmpty() {
        RestAssured.given().contentType(APPLICATION_JSON).get("/object-to-empty").then()
                .statusCode(OK).body(is(emptyOrNullString())).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is("0"));
    }

    @Test
    void test_invalidJsonObject() {
        RestAssured.given().contentType(APPLICATION_JSON).get("/invalid-json").then()
                .statusCode(BAD_REQUEST).body(containsString("JSON"));
    }
}
