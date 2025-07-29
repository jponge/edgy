package org.acme.edgy.test;

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
import org.acme.edgy.runtime.builtins.requests.RequestHeaderAdder;
import org.acme.edgy.runtime.builtins.requests.RequestHeaderRemover;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Objects;

import static org.hamcrest.Matchers.is;

public class EdgyRequestTransformersTest {

    @ApplicationScoped
    public static class RoutingProvider {

        @Produces
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/hello-1", Origin.of("http://localhost:8081/test/yolo-header"), PathMode.FIXED)
                            .addRequestTransformer(new RequestHeaderAdder("X-YOLO", "Yolo"))
                            .addRequestTransformer(new RequestHeaderAdder("X-ABC", "abc"))
                            .addRequestTransformer(new RequestHeaderRemover("X-ABC"))
                    );
        }
    }

    @ApplicationScoped
    @Path("/test")
    public static class TestApi {

        @GET
        @Path("yolo-header")
        public RestResponse<String> yoloHeader(@RestHeader("X-YOLO") String yolo, @RestHeader("X-ABC") String abc) {
            if (abc != null) {
                return RestResponse.status(500, "Header 'abc' was present");
            }
            if (yolo == null) {
                return RestResponse.status(500, "Header 'yolo' was not present");
            }
            return RestResponse.ok(yolo);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    public void test_helloProxy() {
        RestAssured.given()
                .get("/hello-1")
                .then()
                .statusCode(200)
                .body(is("Yolo"));
    }
}
