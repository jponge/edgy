package org.acme.edgy.test;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.acme.edgy.runtime.api.PathMode.REGEXP;
import static org.hamcrest.Matchers.is;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;


class EdgyRegexpPathTest {

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration basicRouting() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/[a-c]+/.*", Origin.of("http://localhost:8081/test"),
                            REGEXP))
                    .addRoute(new Route("/user/[0-9]+/profile",
                            Origin.of("http://localhost:8081/test"), REGEXP))
                    .addRoute(new Route("/api/v[0-9]+/resource/[a-zA-Z0-9_-]+",
                            Origin.of("http://localhost:8081/test"), REGEXP))
                    .addRoute(new Route("/complex/([a-z]+)-(\\d{2,4})/item/(foo|bar)",
                            Origin.of("http://localhost:8081/test"), REGEXP));
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        public String hello() {
            return "Hello!";
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_regexPath_expected200() {
        RestAssured.given().get("/acaccacacacaccaca/something").then().statusCode(OK)
                .body(is("Hello!"));
        RestAssured.given().get("/user/123/profile").then().statusCode(OK).body(is("Hello!"));
        RestAssured.given().get("/user/0/profile").then().statusCode(OK).body(is("Hello!"));
        RestAssured.given().get("/api/v2/resource/abc-123_X").then().statusCode(OK)
                .body(is("Hello!"));
        RestAssured.given().get("/api/v10/resource/Resource_42").then().statusCode(OK)
                .body(is("Hello!"));
        RestAssured.given().get("/complex/abc-2022/item/foo").then().statusCode(OK)
                .body(is("Hello!"));
        RestAssured.given().get("/complex/xyz-99/item/bar").then().statusCode(OK)
                .body(is("Hello!"));
    }

    @Test
    void test_regexPath_expected404() {
        RestAssured.given().get("/acaccacacdacaccaca/something").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/user/abc/profile").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/user//profile").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/api/v/resource/abc").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/api/v2/resource/").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/complex/ABC-2022/item/foo").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/complex/abc-1/item/foo").then().statusCode(NOT_FOUND);
        RestAssured.given().get("/complex/abc-2022/item/baz").then().statusCode(NOT_FOUND);
    }
}
