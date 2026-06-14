package org.acme.edgy.test.basic;

import static org.acme.edgy.runtime.api.utils.StatusCode.BAD_GATEWAY;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;

import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

class EdgyIdleTimeoutTest {

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
        public String hello(@QueryParam("delay") long delay) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Hello!";
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("edgy.origin.origin-1.idle-timeout", "1")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_helloProxy_ok() {
        RestAssured.given()
                .queryParam("delay", 100)
                .get("/hello")
                .then()
                .statusCode(OK)
                .body(is("Hello!"));
    }

    @Test
    void test_helloProxy_timeout() {
        RestAssured.given()
                .queryParam("delay", 2000)
                .get("/hello")
                .then()
                .statusCode(BAD_GATEWAY); // should probably be 504
    }
}
