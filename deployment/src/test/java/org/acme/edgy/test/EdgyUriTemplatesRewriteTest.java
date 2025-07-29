package org.acme.edgy.test;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.UriInfo;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.is;

public class EdgyUriTemplatesRewriteTest {

    @ApplicationScoped
    public static class RoutingProvider {

        @Produces
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/v1/*", Origin.of("http://localhost:8081/test/dump/{__REQUEST_URI__}"), PathMode.PREFIX))
                    .addRoute(new Route("/v2/*", Origin.of("http://localhost:8081/test/dump/{__REQUEST_URI_AFTER_PREFIX__}"), PathMode.PREFIX))
                    ;
        }
    }

    @ApplicationScoped
    @Path("/test/greet/{who}")
    public static class TestApi {

        @GET
        public String hello(String who) {
            return "Hello " + who;
        }
    }

    @ApplicationScoped
    @Path("/test/dump/{var:.*}")
    public static class DumpApi {

        @GET
        public String dump(UriInfo uriInfo) {
            return uriInfo.getPath();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class, DumpApi.class));

    @Test
    public void test_full_request_uri() {
        RestAssured.given()
                .get("/v1/foo/bar")
                .then()
                .statusCode(200)
                .body(is("/test/dump//v1/foo/bar"));
    }

    @Test
    public void test_request_uri_after_prefix() {
        RestAssured.given()
                .get("/v2/foo/bar")
                .then()
                .statusCode(200)
                .body(is("/test/dump/foo/bar"));
    }
}
