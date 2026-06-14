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

class EdgyBasicTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/hello", Origin.of("origin-1", "http://localhost:8081/test/hello")));
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
    void test_helloProxy() {
        RestAssured.given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello!"));
    }
}
