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
import org.acme.edgy.runtime.builtins.predicates.HeaderPredicate;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

class EdgyHeaderPredicateTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/header-exact", Origin.of("header-exact", "http://localhost:8081/test/hello"))
                            .setPredicate(new HeaderPredicate("X-Api-Version", "v2")))
                    .addRoute(new Route("/header-regex", Origin.of("header-regex", "http://localhost:8081/test/hello"))
                            .setPredicate(new HeaderPredicate("X-Request-Id", Pattern.compile("\\d+")))).build();
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
    void test_headerExact_match() {
        RestAssured.given()
                .header("X-Api-Version", "v2")
                .get("/header-exact")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello!"));
    }

    @Test
    void test_headerExact_noMatch() {
        RestAssured.given()
                .header("X-Api-Version", "v1")
                .get("/header-exact")
                .then()
                .statusCode(StatusCode.NOT_FOUND);
    }

    @Test
    void test_headerExact_missingHeader() {
        RestAssured.given()
                .get("/header-exact")
                .then()
                .statusCode(StatusCode.NOT_FOUND);
    }

    @Test
    void test_headerRegex_match() {
        RestAssured.given()
                .header("X-Request-Id", "12345")
                .get("/header-regex")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello!"));
    }

    @Test
    void test_headerRegex_noMatch() {
        RestAssured.given()
                .header("X-Request-Id", "not-a-number")
                .get("/header-regex")
                .then()
                .statusCode(StatusCode.NOT_FOUND);
    }
}
