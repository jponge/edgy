package org.acme.edgy.test.basic;

import static org.hamcrest.Matchers.is;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

class EdgyBasicPredicateTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration predicatesRouting() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/hello", Origin.of("origin-1", "http://localhost:8081/test/hello"))
                            .setPredicate(
                                    rc -> "baz".equals(rc.request().getHeader(
                                            "X-FOO-BAR"))))
                    .addRoute(new Route("/hello", Origin.of("origin-2", "http://localhost:8081/test/hello"))
                            .setPredicate(rc -> true && rc.request().getHeader("X-YOLO") != null)).build();
        }
    }

    @Path("/test/hello")
    static class TestApi {

        @GET
        public String hello() {
            return "Hello!";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_helloProxy_no_header() {
        RestAssured.given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.NOT_FOUND);
    }

    @Test
    public void test_helloProxy_with_header() {
        RestAssured.given()
                .header("X-FOO-BAR", "baz")
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello!"));
    }

    @Test
    public void test_helloProxy_fallback() {
        RestAssured.given()
                .header("X-YOLO", "Yolo!")
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello!"));
    }
}
