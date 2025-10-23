package org.acme.edgy.runtime.builtins.responses;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
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

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.CoreMatchers.is;

class ResponseContentTypeModifierTest {

    private record Payload(String hello) {
    }

    private static final Payload payloadObject = new Payload("world");
    private static final String TEXTIFIED_JSON_PAYLOAD = "{\"hello\":\"world\"}";

    @ApplicationScoped
    static class RoutingProvider {
        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/json-to-plain",
                            Origin.of("http://localhost:8081/test/json-to-plain"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseContentTypeModifier(TEXT_PLAIN)))
                    .addRoute(new Route("/plain-to-json",
                            Origin.of("http://localhost:8081/test/plain-to-json"), PathMode.FIXED)
                                    .addResponseTransformer(
                                            new ResponseContentTypeModifier(APPLICATION_JSON)));
        }
    }

    @Path("/test")
    static class TestApi {
        @GET
        @Path("/json-to-plain")
        @jakarta.ws.rs.Produces(APPLICATION_JSON) // changes to text/plain
        public RestResponse<Payload> jsonToPlain() {
            return RestResponse.ok(payloadObject);
        }

        @GET
        @Path("/plain-to-json")
        @jakarta.ws.rs.Produces(TEXT_PLAIN) // changes to application/json
        public RestResponse<String> plainToJson() {
            return RestResponse.ok(TEXTIFIED_JSON_PAYLOAD);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_responseReplacesJsonToPlain() {
        RestAssured.given().get("/json-to-plain").then().statusCode(OK).and()
                .contentType(TEXT_PLAIN).and().body(is(TEXTIFIED_JSON_PAYLOAD));
    }

    @Test
    void test_responseReplacesPlainToJson() {
        Payload actualPayload = RestAssured.given().get("/plain-to-json").then().statusCode(OK)
                .and().contentType(APPLICATION_JSON).extract().as(Payload.class);
        assertEquals(payloadObject, actualPayload);
    }
}
