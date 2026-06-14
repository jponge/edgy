package org.acme.edgy.test.basic;

import static org.acme.edgy.runtime.api.utils.StatusCode.NOT_FOUND;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;

import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.UriInfo;
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

class EdgyBasicWildcardTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/v1/*",
                            Origin.of("origin-1", "http://localhost:8081/test/dump/{requestURI}")))
                    .addRoute(new Route("/v2/*",
                            Origin.of("origin-2", "http://localhost:8081/test/dump/{suffix}")))
                    // implicit {suffix} - origin has no template vars, auto-appended
                    .addRoute(new Route("/v3/*",
                            Origin.of("origin-3", "http://localhost:8081/test/dump")))
                    .addRoute(new Route("/api/{version}/*",
                            Origin.of("origin-4", "http://localhost:8081/test/dump/{version}/{suffix}")));
        }
    }

    @Path("/test/dump/{var:.*}")
    static class DumpApi {

        @GET
        public String dump(UriInfo uriInfo) {
            return uriInfo.getPath();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, DumpApi.class));

    @Test
    void test_explicitRequestURI_multipleSegments() {
        RestAssured.given().get("/v1/foo/bar").then().statusCode(OK)
                .body(is("/test/dump/v1/foo/bar"));
    }

    @Test
    void test_explicitRequestURI_singleSegment() {
        RestAssured.given().get("/v1/hello").then().statusCode(OK)
                .body(is("/test/dump/v1/hello"));
    }

    @Test
    void test_explicitRequestURI_deeplyNested() {
        RestAssured.given().get("/v1/a/b/c/d/e").then().statusCode(OK)
                .body(is("/test/dump/v1/a/b/c/d/e"));
    }

    @Test
    void test_explicitSuffix_multipleSegments() {
        RestAssured.given().get("/v2/foo/bar").then().statusCode(OK)
                .body(is("/test/dump/foo/bar"));
    }

    @Test
    void test_explicitSuffix_singleSegment() {
        RestAssured.given().get("/v2/hello").then().statusCode(OK)
                .body(is("/test/dump/hello"));
    }

    @Test
    void test_implicitSuffix_multipleSegments() {
        RestAssured.given().get("/v3/foo/bar").then().statusCode(OK)
                .body(is("/test/dump/foo/bar"));
    }

    @Test
    void test_implicitSuffix_singleSegment() {
        RestAssured.given().get("/v3/hello").then().statusCode(OK)
                .body(is("/test/dump/hello"));
    }

    @Test
    void test_implicitSuffix_deeplyNested() {
        RestAssured.given().get("/v3/a/b/c/d/e").then().statusCode(OK)
                .body(is("/test/dump/a/b/c/d/e"));
    }

    @Test
    void test_wildcardWithSegmentParam() {
        RestAssured.given().get("/api/v1/users/list").then().statusCode(OK)
                .body(is("/test/dump/v1/users/list"));
        RestAssured.given().get("/api/v2/items").then().statusCode(OK)
                .body(is("/test/dump/v2/items"));
    }

    @Test
    void test_explicitSuffix_encodedValues() {
        RestAssured.given().get("/v2/hello%20world").then().statusCode(OK)
                .body(is("/test/dump/hello%20world"));
    }

    @Test
    void test_implicitSuffix_encodedValues() {
        RestAssured.given().get("/v3/path%2Fwith%2Fslashes").then().statusCode(OK)
                .body(is("/test/dump/path%2Fwith%2Fslashes"));
    }

    @Test
    void test_wildcardPrefix_trailingSlash() {
        // /v1/ matches /* with empty suffix
        RestAssured.given().get("/v1/").then().statusCode(OK);
    }

    @Test
    void test_wildcardPrefix_noMatch_exactPrefix() {
        // /v1 alone (without trailing content after /*) should NOT match
        // this is a Vert.x 5 inspired feature
        RestAssured.given().get("/v1").then().statusCode(NOT_FOUND);
    }

    @Test
    public void test_wildcardPrefix_noMatch_wrongPrefix() {
        RestAssured.given().get("/v9/foo").then().statusCode(NOT_FOUND);
    }

    @Test
    public void test_wildcardPrefix_noMatch_noSuffix() {
        // /api without version segment and wildcard content
        RestAssured.given().get("/api").then().statusCode(NOT_FOUND);
    }
}
