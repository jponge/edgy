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

class ResponseJsonArrayBodyModifierTest {

    private static final String ORIGINAL_JSON = "[\"Lorem\",\"Ipsum\",\"Dolor\",\"Sit\",\"Amet\"]";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/remove-element",
                    Origin.of("origin-1", "http://localhost:8081/test/remove-element"), PathMode.FIXED)
                            .addResponseTransformer(new ResponseJsonArrayBodyModifier(json -> {
                                json.remove(1); // Remove "Ipsum"
                                return json;
                            })))
                    .addRoute(new Route("/modify-element",
                            Origin.of("origin-2", "http://localhost:8081/test/modify-element"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJsonArrayBodyModifier(json -> {
                                                json.set(0, "Changed");
                                                return json;
                                            })))
                    .addRoute(new Route("/add-element",
                            Origin.of("origin-3", "http://localhost:8081/test/add-element"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJsonArrayBodyModifier(json -> {
                                                json.add("NewElement");
                                                return json;
                                            })))
                    .addRoute(new Route("/set-null-dynamic",
                            Origin.of("origin-4", "http://localhost:8081/test/set-null-dynamic"),
                                    PathMode.FIXED).addResponseTransformer(
                                    new ResponseJsonArrayBodyModifier(json -> null)))
                    .addRoute(new Route("/set-null-static",
                            Origin.of("origin-5", "http://localhost:8081/test/set-null-static"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJsonArrayBodyModifier((JsonArray) null)))
                    .addRoute(new Route(
                            "/replace-full", Origin.of("origin-6", "http://localhost:8081/test/replace-full"),
                                    PathMode.FIXED).addResponseTransformer(
                                    new ResponseJsonArrayBodyModifier(new JsonArray().add(1).add(2)
                                            .add(new JsonObject().put("key", "value")))))
                    .addRoute(new Route("/invalid-json",
                            Origin.of("origin-7", "http://localhost:8081/test/invalid-json"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseJsonArrayBodyModifier(json -> json)));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/remove-element")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String removeElementEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/modify-element")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String modifyElementEndpoint() {
            return ORIGINAL_JSON;
        }

        @GET
        @Path("/add-element")
        @jakarta.ws.rs.Produces(APPLICATION_JSON)
        public String addElementEndpoint() {
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
    void test_removeJSONElement() {
        final String expectedResponseBody = "[\"Lorem\",\"Dolor\",\"Sit\",\"Amet\"]";
        RestAssured.given().contentType(APPLICATION_JSON).get("/remove-element").then()
                .statusCode(OK).body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_modifyJSONElement() {
        final String expectedResponseBody = "[\"Changed\",\"Ipsum\",\"Dolor\",\"Sit\",\"Amet\"]";
        RestAssured.given().contentType(APPLICATION_JSON).get("/modify-element").then()
                .statusCode(OK).body(is(expectedResponseBody)).and().contentType(APPLICATION_JSON)
                .and().header(CONTENT_LENGTH, is(String.valueOf(expectedResponseBody.length())));
    }

    @Test
    void test_addJSONElement() {
        final String expectedResponseBody =
                "[\"Lorem\",\"Ipsum\",\"Dolor\",\"Sit\",\"Amet\",\"NewElement\"]";
        RestAssured.given().contentType(APPLICATION_JSON).get("/add-element").then().statusCode(OK)
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
        final String expectedResponseBody = "[1,2,{\"key\":\"value\"}]";
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
