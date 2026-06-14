package org.acme.edgy.test.basic;

import static org.hamcrest.Matchers.is;

import java.util.regex.Pattern;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.acme.edgy.runtime.builtins.predicates.QueryParameterPredicate;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

class EdgyQueryParameterPredicateTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/query-exists", Origin.of("query-exists", "http://localhost:8081/test/hello"))
                            .setPredicate(new QueryParameterPredicate("debug")))
                    .addRoute(new Route("/query-exact", Origin.of("query-exact", "http://localhost:8081/test/hello"))
                            .setPredicate(new QueryParameterPredicate("version", "2")))
                    .addRoute(new Route("/query-regex", Origin.of("query-regex", "http://localhost:8081/test/hello"))
                            .setPredicate(new QueryParameterPredicate("token", Pattern.compile("[a-f0-9]{8}"))));
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/hello")
        public String hello() {
            return "Hello!";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_queryExists_present() {
        RestAssured.given()
                .queryParam("debug", "true")
                .get("/query-exists")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello!"));
    }

    @Test
    void test_queryExists_absent() {
        RestAssured.given()
                .get("/query-exists")
                .then()
                .statusCode(StatusCode.NOT_FOUND);
    }

    @Test
    void test_queryExact_match() {
        RestAssured.given()
                .queryParam("version", "2")
                .get("/query-exact")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello!"));
    }

    @Test
    void test_queryExact_noMatch() {
        RestAssured.given()
                .queryParam("version", "3")
                .get("/query-exact")
                .then()
                .statusCode(StatusCode.NOT_FOUND);
    }

    @Test
    void test_queryRegex_match() {
        RestAssured.given()
                .queryParam("token", "abcd1234")
                .get("/query-regex")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello!"));
    }

    @Test
    void test_queryRegex_noMatch() {
        RestAssured.given()
                .queryParam("token", "not-hex")
                .get("/query-regex")
                .then()
                .statusCode(StatusCode.NOT_FOUND);
    }
}
