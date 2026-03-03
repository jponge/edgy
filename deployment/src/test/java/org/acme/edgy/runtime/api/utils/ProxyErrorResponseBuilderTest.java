package org.acme.edgy.runtime.api.utils;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;

import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

class ProxyErrorResponseBuilderTest {

    static class RoutingProvider {

        @Produces
        RoutingConfiguration routingConfiguration() {

            RequestTransformer requestAssertFailure = new RequestTransformer() {
                @Override
                public Future<ProxyResponse> apply(ProxyContext context) {
                    return Assertions.fail();
                }
            };

            ResponseTransformer responseAssertFailure = new ResponseTransformer() {
                @Override
                public Future<Void> apply(ProxyContext context) {
                    return Assertions.fail();
                }
            };

            ResponseTransformer responseTransformerInvokingBadRequest = new ResponseTransformer() {
                @Override
                public Future<Void> apply(ProxyContext context) {
                    return ProxyErrorResponseBuilder.create(context)
                            .badRequest()
                            .message("Bad Request")
                            .sendResponseInResponseTransformer();
                }
            };

            RequestTransformer requestTransformerInvokingBadRequest = new RequestTransformer() {
                @Override
                public Future<ProxyResponse> apply(ProxyContext context) {
                    return ProxyErrorResponseBuilder.create(context)
                            .badRequest()
                            .message("Bad Request")
                            .sendResponseInRequestTransformer();
                }
            };

            return new RoutingConfiguration()
                    .addRoute(new Route("/request-transformer",
                            Origin.of("origin-1", "origin uri is never called"), PathMode.FIXED)
                            .addRequestTransformer(requestTransformerInvokingBadRequest)
                            .addRequestTransformer(requestAssertFailure)
                            .addResponseTransformer(responseAssertFailure))
                    .addRoute(new Route("/response-transformer",
                            Origin.of("origin-2", "http://localhost:8081/test/response-transformer"),
                            PathMode.FIXED).addResponseTransformer(responseAssertFailure)
                            .addResponseTransformer(
                                    responseTransformerInvokingBadRequest));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/response-transformer")
        public RestResponse<Void> endpoint() {
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class).addClasses(RoutingProvider.class));

    @Test
    void testRequestTransformerFailureBreaksChain() {
        RestAssured.given().get("/request-transformer").then().statusCode(BAD_REQUEST).and()
                .contentType(TEXT_PLAIN).and().body(is("Bad Request"));
    }

    @Test
    void testResponseTransformerFailureBreaksChain() {
        RestAssured.given().get("/response-transformer").then().statusCode(BAD_REQUEST).and()
                .contentType(TEXT_PLAIN).and().body(is("Bad Request"));
    }
}
