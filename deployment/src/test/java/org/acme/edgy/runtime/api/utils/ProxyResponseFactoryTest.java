package org.acme.edgy.runtime.api.utils;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.builtins.requests.RequestHeaderAdder;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import static org.hamcrest.CoreMatchers.is;

class ProxyResponseFactoryTest {

    @ApplicationScoped
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

            ResponseTransformer responseTransformerInvokingBadRequestFactory =
                    new ResponseTransformer() {
                        @Override
                        public Future<Void> apply(ProxyContext context) {
                            return ProxyResponseFactory.badRequestInResponseTransformer(context,
                                    "Bad Request");
                        }
                    };

            RequestTransformer requestTransformerInvokingBadRequestFactory =
                    new RequestTransformer() {
                        @Override
                        public Future<ProxyResponse> apply(ProxyContext context) {
                            return ProxyResponseFactory.badRequestInRequestTransformer(context,
                                    "Bad Request");
                        }
                    };

            return new RoutingConfiguration()
                    .addRoute(new Route("/request-transformer",
                            Origin.of("origin-1", "origin uri is never called"), PathMode.FIXED)
                            .addRequestTransformer(requestTransformerInvokingBadRequestFactory)
                            .addRequestTransformer(requestAssertFailure)
                            .addResponseTransformer(responseAssertFailure))
                    .addRoute(new Route("/response-transformer",
                            Origin.of("origin-2", "http://localhost:8081/test/response-transformer"),
                                    PathMode.FIXED).addResponseTransformer(responseAssertFailure)
                                    .addResponseTransformer(
                                            responseTransformerInvokingBadRequestFactory));
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
