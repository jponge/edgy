package org.acme.edgy.test.regex;

import static org.acme.edgy.runtime.api.PathMode.REGEXP;
import static org.acme.edgy.runtime.api.utils.StatusCode.NOT_FOUND;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

class EdgyRegExpSegmentTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/regexp/(?<userId>[0-9]+)/(?<action>[a-z]+)",
                            Origin.of("origin-1", "http://localhost:8081/test/{action}/{userId}"), REGEXP))
                    .addRoute(new Route("/echo/(?<word>[a-z]+)",
                            Origin.of("origin-2", "http://localhost:8081/test/{word}/{word}"), REGEXP))
                    .addRoute(new Route("/span/(?<middle>.*)/end",
                            Origin.of("origin-3", "http://localhost:8081/test/{middle}"), REGEXP))
                    .addRoute(new Route("/back/(?<x>[^/]+)/\\k<x>",
                            Origin.of("origin-4", "http://localhost:8081/test/{x}"), REGEXP)).build();
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/{a}/{b}")
        public String twoSegments(@PathParam("a") String a, @PathParam("b") String b) {
            return a + "/" + b;
        }

        @GET
        @Path("/{var:.*}")
        public String catchAll(@PathParam("var") String var) {
            return var;
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_namedGroups_reversedInOrigin() {
        RestAssured.given().get("/regexp/123/view").then().statusCode(OK)
                .body(is("view/123"));
        RestAssured.given().get("/regexp/42/edit").then().statusCode(OK)
                .body(is("edit/42"));
    }

    @Test
    void test_namedGroups_noMatch() {
        RestAssured.given().get("/regexp/abc/view").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/regexp/123/VIEW").then().statusCode(NOT_FOUND);
    }

    @Test
    void test_namedGroup_reusedInOrigin() {
        RestAssured.given().get("/echo/hello").then().statusCode(OK)
                .body(is("hello/hello"));
    }

    @Test
    void test_wildcardGroupSpansMultipleSegments() {
        RestAssured.given().get("/span/b/c/d/e/f/end").then().statusCode(OK)
                .body(is("b/c/d/e/f"));
        RestAssured.given().get("/span/x/end").then().statusCode(OK)
                .body(is("x"));
    }

    @Test
    void test_wildcardGroupSpansMultipleSegments_noMatch() {
        RestAssured.given().get("/span/b/c/d/e/f").then().statusCode(NOT_FOUND);
    }

    @Test
    void test_backreference_matchesSameValue() {
        RestAssured.given().get("/back/hello/hello").then().statusCode(OK)
                .body(is("hello"));
    }

    @Test
    void test_backreference_differentValues() {
        RestAssured.given().get("/back/hello/world").then().statusCode(NOT_FOUND);
    }
}
