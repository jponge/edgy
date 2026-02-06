package org.acme.edgy.test;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

class EdgySegmentParamsTest {

    static class RoutingProvider {

        @Produces
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    // simple segment params tests
                    .addRoute(new Route("/reverse/{a}/{b}",
                            Origin.of("origin-1", "http://localhost:8081/test/{b}/{a}"), PathMode.PARAMS))
                    .addRoute(new Route("/three/{segment}",
                            Origin.of("origin-2", "http://localhost:8081/test/{segment}/{segment}/{segment}"),
                                    PathMode.PARAMS))
                    .addRoute(new Route("/path-to-query/{1}/{2}",
                            Origin.of("origin-3", "http://localhost:8081/test?first={1}&second={2}"),
                                    PathMode.PARAMS))
                    .addRoute(new Route("/query-to-path",
                            // not PathMode dependent
                            Origin.of("origin-4", "http://localhost:8081/test/{b}/{a}"), PathMode.PARAMS))
                    .addRoute(new Route("/escaped-regex/.*/{a}",
                            Origin.of("origin-5", "http://localhost:8081/test/{a}"), PathMode.PARAMS))
                    .addRoute(new Route("/joined/{a}-{b}",
                            Origin.of("origin-6", "http://localhost:8081/test/joined/{b}-{a}"),
                                    PathMode.PARAMS))
                    // regex segment params tests
                    .addRoute(new Route("/users/{<userId>\\d+}",
                            Origin.of("origin-7", "http://localhost:8081/test/user-{userId}"), PathMode.PARAMS))
                    .addRoute(new Route("/files/{<hash>[a-f0-9]+}",
                            Origin.of("origin-8", "http://localhost:8081/test/file/{hash}"), PathMode.PARAMS))
                    // mixed simple and regex segment params test
                    .addRoute(new Route("/mixed/{name}/order/{<orderId>\\d+}",
                            Origin.of("origin-9", "http://localhost:8081/test/{name}/{orderId}"),
                                    PathMode.PARAMS));
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

        @GET
        @Path("/joined/hello-world")
        public RestResponse<Void> joinedEndpoint() {
            return RestResponse.ok();
        }

        @GET
        @Path("/user-{userId}")
        public RestResponse<Void> userWithIdEndpoint(@PathParam("userId") String userId) {
            // userId should be digits only
            if (userId != null && userId.matches("\\d+")) {
                return RestResponse.ok();
            }
            return RestResponse.serverError();
        }

        @GET
        @Path("/file/{hash}")
        public RestResponse<Void> fileHashEndpoint(@PathParam("hash") String hash) {
            // hash should be hex digits only
            if (hash != null && hash.matches("[a-f0-9]+")) {
                return RestResponse.ok();
            }
            return RestResponse.serverError();
        }

        @GET
        @Path("/{name}/{orderId}")
        public RestResponse<Void> mixedEndpoint(@PathParam("name") String name,
                @PathParam("orderId") String orderId) {
            // orderId should be digits only, name can be anything
            if (name != null && orderId != null && orderId.matches("\\d+")) {
                return RestResponse.ok();
            }
            return RestResponse.serverError();
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

    @Test
    void test_joinedSegment() {
        RestAssured.given().get("/joined/world-hello").then().statusCode(OK);
        RestAssured.given().get("/joined/world-hello/").then().statusCode(OK);
    }

    @Test
    void test_customRegexSegment_onlyDigits() {
        // Should match digits only
        RestAssured.given().get("/users/123").then().statusCode(OK);
        RestAssured.given().get("/users/456789").then().statusCode(OK);

        // Should NOT match non-digits
        RestAssured.given().get("/users/abc").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/users/12abc").then().statusCode(NOT_FOUND);

        // With trailing slash
        RestAssured.given().get("/users/123/").then().statusCode(OK);
        RestAssured.given().get("/users/456789/").then().statusCode(OK);
        RestAssured.given().get("/users/abc/").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/users/12abc/").then().statusCode(NOT_FOUND);
    }

    @Test
    void test_customRegexSegment_hexOnly() {
        // Should match hex digits only (lowercase)
        RestAssured.given().get("/files/abc123").then().statusCode(OK);
        RestAssured.given().get("/files/deadbeef").then().statusCode(OK);

        // Should NOT match invalid hex
        RestAssured.given().get("/files/xyz").then().statusCode(NOT_FOUND);
        // uppercase letters not in pattern
        RestAssured.given().get("/files/DEADBEEF").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/files/abc123xyz").then().statusCode(NOT_FOUND);

        // With trailing slash
        RestAssured.given().get("/files/abc123/").then().statusCode(OK);
        RestAssured.given().get("/files/deadbeef/").then().statusCode(OK);
        RestAssured.given().get("/files/xyz/").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/files/DEADBEEF/").then().statusCode(NOT_FOUND);
    }

    @Test
    void test_mixedSimpleAndCustomRegex() {
        // name can be anything, orderId must be digits
        RestAssured.given().get("/mixed/john/order/123").then().statusCode(OK);
        RestAssured.given().get("/mixed/jane-doe/order/456").then().statusCode(OK);
        RestAssured.given().get("/mixed/user_123/order/789").then().statusCode(OK);

        // orderId must be digits - should fail
        RestAssured.given().get("/mixed/john/order/abc").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/mixed/john/order/12abc").then().statusCode(NOT_FOUND);

        // With trailing slash
        RestAssured.given().get("/mixed/john/order/123/").then().statusCode(OK);
        RestAssured.given().get("/mixed/jane-doe/order/456/").then().statusCode(OK);
        RestAssured.given().get("/mixed/john/order/abc/").then().statusCode(NOT_FOUND);
    }
}
