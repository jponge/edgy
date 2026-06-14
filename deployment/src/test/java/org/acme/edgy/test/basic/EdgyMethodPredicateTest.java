package org.acme.edgy.test.basic;

import static org.hamcrest.Matchers.is;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.acme.edgy.runtime.builtins.predicates.MethodPredicate;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpMethod;

class EdgyMethodPredicateTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/method", Origin.of("method-post", "http://localhost:8081/test/post"))
                            .setPredicate(new MethodPredicate(HttpMethod.POST)));
        }
    }

    @Path("/test")
    static class TestApi {

        @POST
        @Path("/post")
        public String post() {
            return "Posted!";
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_method_match() {
        RestAssured.given()
                .post("/method")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Posted!"));
    }

    @Test
    void test_method_noMatch() {
        RestAssured.given()
                .get("/method")
                .then()
                .statusCode(StatusCode.NOT_FOUND);
    }
}
