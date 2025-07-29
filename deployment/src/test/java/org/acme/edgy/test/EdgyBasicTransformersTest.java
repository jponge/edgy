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
import org.acme.edgy.runtime.builtins.responses.ResponseHeaderAdder;
import org.acme.edgy.runtime.builtins.responses.ResponseHeaderRemover;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class EdgyBasicTransformersTest {

    @ApplicationScoped
    public static class RoutingProvider {

        @Produces
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/hello-1", Origin.of("http://localhost:8081/test/yolo-abc-headers"), PathMode.FIXED)
                            .addRequestTransformer(new RequestHeaderAdder("X-YOLO", "Yolo"))
                            .addRequestTransformer(new RequestHeaderAdder("X-ABC", "abc"))
                            .addRequestTransformer(new RequestHeaderRemover("X-ABC"))
                    )
                    .addRoute(new Route("/hello-2", Origin.of("http://localhost:8081/test/yolo-header-maker"), PathMode.FIXED)
                            .addResponseTransformer(new ResponseHeaderAdder("X-ADDED", "123-abc"))
                            .addResponseTransformer(new ResponseHeaderRemover("X-DROP-ME"))
                    );
        }
    }

    @ApplicationScoped
    @Path("/test")
    public static class TestApi {

        @GET
        @Path("yolo-abc-headers")
        public RestResponse<String> yoloAndAbcHeaders(@RestHeader("X-YOLO") String yolo, @RestHeader("X-ABC") String abc) {
            if (abc != null) {
                return RestResponse.status(500, "Header 'abc' was present");
            }
            if (yolo == null) {
                return RestResponse.status(500, "Header 'yolo' was not present");
            }
            return RestResponse.ok(yolo);
        }

        @GET
        @Path("yolo-header-maker")
        public RestResponse<String> yoloHeaderMaker() {
            return RestResponse.ResponseBuilder.ok("Yolo")
                    .header("X-YOLO", "Yolo")
                    .header("X-DROP-ME", "I must have been dropped")
                    .build();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    public void test_yoloAndAbcHeaders() {
        RestAssured.given()
                .get("/hello-1")
                .then()
                .statusCode(200)
                .body(is("Yolo"));
    }

    @Test
    public void test_yoloHeaderMaker() {
        RestAssured.given()
                .get("/hello-2")
                .then()
                .statusCode(200)
                .body(is("Yolo"))
                .header("X-YOLO", is("Yolo"))
                .header("X-ADDED", is("123-abc"))
                .header("X-DROP-ME", nullValue());
    }
}
