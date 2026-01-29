package org.acme.edgy.runtime.builtins.requests;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.PUT;
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

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpMethod;

class RequestHttpMethodModifierTest {

    @ApplicationScoped
    static class RoutingProvider {

        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration().addRoute(new Route("/post-to-put",
                    Origin.of("origin-1", "http://localhost:8081/test/post-to-put"), PathMode.FIXED)
                            .addRequestTransformer(new RequestHttpMethodModifier(HttpMethod.PUT)));

        }
    }

    @Path("/test")
    static class TestApi {

        @PUT
        @Path("/post-to-put")
        public RestResponse<Void> endpoint() {
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_changeHttpMethodPostToPut() {
        RestAssured.given().when().post("/post-to-put").then().statusCode(OK);

    }
}
