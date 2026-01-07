package org.acme.edgy.runtime.builtins.responses;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.PAYLOAD_TOO_LARGE;

class ResponseContentLengthLimitGuardTest {

    private static final long CONTENT_LENGTH_LIMIT = 16L;

    @ApplicationScoped
    static class RoutingProvider {

        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/content-length-limit",
                    Origin.of("origin-1", "http://localhost:8081/test/content-length-limit"), PathMode.FIXED)
                            .addResponseTransformer(
                                    new ResponseContentLengthLimitGuard(CONTENT_LENGTH_LIMIT)));
        }
    }

    @Path("/test")
    static class TestApi {

        @POST
        @Path("/content-length-limit")
        @jakarta.ws.rs.Produces(TEXT_PLAIN)
        public RestResponse<String> endpoint(String body) {
            return RestResponse.ok(body);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_contentLengthBellowLimit() {
        RestAssured.given().contentType(TEXT_PLAIN).body("a".repeat((int) CONTENT_LENGTH_LIMIT - 1))
                .post("/content-length-limit").then().statusCode(OK);
    }

    @Test
    void test_contentLengthAtLimit() {
        RestAssured.given().contentType(TEXT_PLAIN).body("a".repeat((int) CONTENT_LENGTH_LIMIT))
                .post("/content-length-limit").then().statusCode(OK);
    }

    @Test
    void test_contentLengthAboveLimit() {
        RestAssured.given().contentType(TEXT_PLAIN).body("a".repeat((int) CONTENT_LENGTH_LIMIT + 1))
                .post("/content-length-limit").then().statusCode(PAYLOAD_TOO_LARGE).and()
                .body(containsString("exceeds"));
    }

}
