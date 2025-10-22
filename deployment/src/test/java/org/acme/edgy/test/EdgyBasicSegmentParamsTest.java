package org.acme.edgy.test;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

class EdgyBasicSegmentParamsTest {

    static class RoutingProvider {

        @Produces
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/reverse/{a}/{b}",
                            Origin.of("http://localhost:8081/test/{b}/{a}"), PathMode.PARAMS))
                    .addRoute(new Route("/three/{segment}",
                            Origin.of("http://localhost:8081/test/{segment}/{segment}/{segment}"),
                            PathMode.PARAMS))
                    .addRoute(new Route("/path-to-query/{1}/{2}",
                            Origin.of("http://localhost:8081/test?first={1}&second={2}"),
                            PathMode.PARAMS))
                    .addRoute(new Route("/query-to-path",
                            // not PathMode dependent
                            Origin.of("http://localhost:8081/test/{b}/{a}"), PathMode.PARAMS))
                    .addRoute(new Route("/escaped-regex/.*/{a}",
                            Origin.of("http://localhost:8081/test/{a}"), PathMode.PARAMS));
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/world/hello")
        public RestResponse<Void> reversedPathEndpoint() {
            return RestResponse.ok();
        }

        @GET
        @Path("/hello/hello/hello")
        public RestResponse<Void> threeHelloEndpoint() {
            return RestResponse.ok();
        }

        @GET
        public RestResponse<Void> pathToQueryEndpoint(@QueryParam("first") String first,
                @QueryParam("second") String second) {
            if ("hello".equals(first) && "world".equals(second)) {
                return RestResponse.ok();
            }
            return RestResponse.serverError();
        }

        @GET
        @Path("/escaped-regex-path")
        public RestResponse<Void> escapedRegexPathEndpoint() {
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_reversingSegmentParameters() {
        RestAssured.given().get("/reverse/hello/world").then().statusCode(OK);
        RestAssured.given().get("/reverse/hello/world/").then().statusCode(OK);
    }

    @Test
    void test_reusingSegmentParameters() {
        RestAssured.given().get("/three/hello").then().statusCode(OK);
        RestAssured.given().get("/three/hello/").then().statusCode(OK);
    }

    @Test
    void test_pathParamToQueryParam() {
        RestAssured.given().get("/path-to-query/hello/world").then().statusCode(OK);
        RestAssured.given().get("/path-to-query/hello/world/").then().statusCode(OK);
    }

    @Test
    void test_queryParamToPathParam() {
        // all query params stored in URI template variables, so this is not really PathMode
        // dependent
        RestAssured.given().queryParam("a", "hello").queryParam("b", "world").get("/query-to-path")
                .then().statusCode(OK);
    }

    @Test
    void test_escapedRegexInPath() {
        RestAssured.given().get("/escaped-regex/.*/escaped-regex-path").then().statusCode(OK);
        RestAssured.given().get("/escaped-regex/something/escaped-regex-path").then()
                .statusCode(NOT_FOUND);

        RestAssured.given().get("/escaped-regex/.*/escaped-regex-path/").then().statusCode(OK);
        RestAssured.given().get("/escaped-regex/something/escaped-regex-path/").then()
                .statusCode(NOT_FOUND);
    }
}
