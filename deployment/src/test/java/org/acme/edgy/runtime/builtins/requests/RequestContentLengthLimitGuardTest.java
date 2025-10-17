package org.acme.edgy.runtime.builtins.requests;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.hamcrest.CoreMatchers.containsString;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.PAYLOAD_TOO_LARGE;
import java.util.Objects;
import java.util.function.Function;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import io.vertx.httpproxy.ProxyRequest;

class RequestContentLengthLimitGuardTest {

    private static final long CONTENT_LENGTH_LIMIT = 16L;

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/content-length-limit",
                    Origin.of("http://localhost:8081/test/content-length-limit"), PathMode.FIXED)
                            .addRequestTransformer(
                                    new RequestContentLengthLimitGuard(CONTENT_LENGTH_LIMIT)));
        }
    }

    @Path("/test")
    static class TestApi {
        @POST
        @Path("/content-length-limit")
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
        RestAssured.given().body("a".repeat((int) CONTENT_LENGTH_LIMIT - 1))
                .post("/content-length-limit").then().statusCode(OK);
    }

    @Test
    void test_contentLengthAtLimit() {
        RestAssured.given().body("a".repeat((int) CONTENT_LENGTH_LIMIT))
                .post("/content-length-limit").then().statusCode(OK);
    }

    @Test
    void test_contentLengthAboveLimit() {
        RestAssured.given().body("a".repeat((int) CONTENT_LENGTH_LIMIT + 1))
                .post("/content-length-limit").then().statusCode(PAYLOAD_TOO_LARGE).and()
                .body(containsString("exceeds"));
    }
}
