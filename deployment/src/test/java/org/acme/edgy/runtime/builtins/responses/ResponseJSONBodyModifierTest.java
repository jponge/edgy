package org.acme.edgy.runtime.builtins.responses;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
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

class ResponseJSONBodyModifierTest {

    private static final String ORIGINAL_JSON =
            "{\"1\":\"Lorem\",\"2\":\"Ipsum\",\"3\":[\"Dolor\",\"Sit\",\"Amet\"]}";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/remove-field",
                    Origin.of("http://localhost:8081/test/remove-field"), PathMode.FIXED)
                            .addResponseTransformer(new ResponseJSONBodyModifier(json -> {
                                json.remove("2");
                                return json;
                            })))
                    .addRoute(new Route("/modify-field",
                            Origin.of("http://localhost:8081/test/modify-field"), PathMode.FIXED)
                                    .addResponseTransformer(new ResponseJSONBodyModifier(json -> {
                                        json.put("1", "Changed");
                                        return json;
                                    })))
                    .addRoute(new Route("/add-field",
                            Origin.of("http://localhost:8081/test/add-field"), PathMode.FIXED)
                                    .addResponseTransformer(new ResponseJSONBodyModifier(json -> {
                                        json.put("4", "NewVal");
                                        return json;
                                    })))
                    .addRoute(new Route("/set-null-dynamic",
                            Origin.of("http://localhost:8081/test/set-null-dynamic"),
                            PathMode.FIXED).addResponseTransformer(
                                    new ResponseJSONBodyModifier(json -> null)))
                    .addRoute(new Route("/set-null-static",
                            Origin.of("http://localhost:8081/test/set-null-static"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJSONBodyModifier((JsonObject) null)))
                    .addRoute(new Route("/replace-full",
                            Origin.of("http://localhost:8081/test/replace-full"), PathMode.FIXED)
                                    .addResponseTransformer(new ResponseJSONBodyModifier(
                                            new JsonObject().put("replaced", "yes").put("arr",
                                                    new JsonArray().add(1).add(2)))))
                    .addRoute(new Route("/invalid-json",
                            Origin.of("http://localhost:8081/test/invalid-json"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJSONBodyModifier(json -> json)));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/remove-field")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String removeFieldEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/modify-field")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String modifyFieldEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/add-field")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String addFieldEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/set-null-dynamic")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String setNullDynamicEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/set-null-static")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String setNullStaticEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/replace-full")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String replaceFullEndpoint() {
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
    void test_removeJSONField() {
        final String expectedResponseBody = "{\"1\":\"Lorem\",\"3\":[\"Dolor\",\"Sit\",\"Amet\"]}";
        RestAssured.given().contentType(APPLICATION_JSON).get("/remove-field").then().statusCode(OK)
                .body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON).and()
                .header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_modifyJSONField() {
        final String expectedResponseBody =
                "{\"1\":\"Changed\",\"2\":\"Ipsum\",\"3\":[\"Dolor\",\"Sit\",\"Amet\"]}";
        RestAssured.given().contentType(APPLICATION_JSON).get("/modify-field").then().statusCode(OK)
                .body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON).and()
                .header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_addJSONField() {
        final String expectedResponseBody =
                "{\"1\":\"Lorem\",\"2\":\"Ipsum\",\"3\":[\"Dolor\",\"Sit\",\"Amet\"],\"4\":\"NewVal\"}";
        RestAssured.given().contentType(APPLICATION_JSON).get("/add-field").then().statusCode(OK)
                .body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON).and()
                .header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_setNullDynamic() {
        RestAssured.given().contentType(APPLICATION_JSON).get("/set-null-dynamic").then()
                .statusCode(OK).body(is(emptyOrNullString())).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is("0"));
    }

    @Test
    void test_setNullStatic() {
        RestAssured.given().contentType(APPLICATION_JSON).get("/set-null-static").then()
                .statusCode(OK).body(is(emptyOrNullString())).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is("0"));
    }

    @Test
    void test_replaceFull() {
        final String expectedResponseBody = "{\"replaced\":\"yes\",\"arr\":[1,2]}";
        RestAssured.given().contentType(APPLICATION_JSON).get("/replace-full").then().statusCode(OK)
                .body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON).and()
                .header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_dynamicFailsDueToInvalidJSONBody() {
        RestAssured.given().contentType(APPLICATION_JSON).get("/invalid-json").then()
                .statusCode(BAD_REQUEST).body(containsString("JSON"));
    }
}
