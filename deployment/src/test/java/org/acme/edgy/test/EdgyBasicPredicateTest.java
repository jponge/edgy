package org.acme.edgy.test;

import static org.hamcrest.Matchers.is;

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

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class EdgyBasicPredicateTest {

    @ApplicationScoped
    public static class RoutingProvider {

        @Produces
        RoutingConfiguration predicatesRouting() {
            return new RoutingConfiguration()
                            .addRoute(new Route("/hello", Origin.of("origin-1", "http://localhost:8081/test/hello"),
                                            PathMode.FIXED)
                            .addPredicate(rc ->
                                    "baz".equals(rc.request().getHeader("X-FOO-BAR"))
                            ))
                            .addRoute(new Route("/hello", Origin.of("origin-2", "http://localhost:8081/test/hello"),
                                            PathMode.FIXED)
                            .addPredicate(rc -> true)
                            .addPredicate(rc ->
                                    rc.request().getHeader("X-YOLO") != null
                            ));
        }
    }

    @ApplicationScoped
    @Path("/test/hello")
    public static class TestApi {

        @GET
        public String hello() {
            return "Hello!";
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    public void test_helloProxy_no_header() {
        RestAssured.given()
                .get("/hello")
                .then()
                .statusCode(404);
    }

    @Test
    public void test_helloProxy_with_header() {
        RestAssured.given()
                .header("X-FOO-BAR", "baz")
                .get("/hello")
                .then()
                .statusCode(200)
                .body(is("Hello!"));
    }

    @Test
    public void test_helloProxy_fallback() {
        RestAssured.given()
                .header("X-YOLO", "Yolo!")
                .get("/hello")
                .then()
                .statusCode(200)
                .body(is("Hello!"));
    }
}
