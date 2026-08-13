package org.acme.edgy.test.scatter;

import static org.hamcrest.Matchers.is;

import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.acme.edgy.runtime.api.FailureMode;
import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.acme.edgy.runtime.builtins.transformers.BodyAccumulator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.Body;

class ScatterGatherHappyPathTest {

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addScatterRoute(new ScatterRoute("/scatter")
                            .addLeg(new Leg(Origin.of("s1", "http://localhost:8081/test/a"))
                                    .setMethod(HttpMethod.GET))
                            .addLeg(new Leg(Origin.of("s2", "http://localhost:8081/test/b"))
                                    .setMethod(HttpMethod.GET)
                                    .addRequestTransformer(context -> {
                                        String uri = context.request().getURI();
                                        context.request().setURI(uri + (uri.contains("?") ? "&" : "?") + "xyz=123");
                                        return context.sendRequest();
                                    }))
                            .addLeg(new Leg(Origin.of("s3", "http://localhost:8081/test/target")))
                            .addLeg(new Leg(Origin.of("s4", "http://localhost:8081/test/something"))
                                    .addRequestTransformer(context -> {
                                        Body body = context.request().getBody();
                                        if (body == null) {
                                            return context.sendRequest();
                                        }
                                        return BodyAccumulator.readBodyBuffer(body)
                                                .compose(buf -> {
                                                    Buffer prepended = Buffer.buffer("--- start ---\n")
                                                            .appendBuffer(buf);
                                                    context.request().setBody(Body.body(prepended));
                                                    return context.sendRequest();
                                                });
                                    }))
                            .addLeg(new Leg(Origin.of("s5", "http://localhost:8081/test/search"))
                                    .setMethod(HttpMethod.GET)
                                    .setKeepBody(true))
                            .setComposer(responses -> {
                                String composed = responses.stream()
                                        .map(r -> r.body().toString())
                                        .collect(Collectors.joining("|"));
                                return Future.succeededFuture(Buffer.buffer(composed));
                            })
                            .setFailureMode(FailureMode.PARTIAL))
                    .build();
        }
    }

    @Path("/test")
    static class TestApi {

        @GET
        @Path("/a")
        public String a() {
            return "from-a";
        }

        @GET
        @Path("/b")
        public String b(@QueryParam("xyz") String xyz) {
            return "from-b:xyz=" + xyz;
        }

        @POST
        @Path("/target")
        public String target(String body) {
            return "from-target:body=" + body;
        }

        @POST
        @Path("/something")
        public String something(String body) {
            return "from-something:body=" + body;
        }

        @GET
        @Path("/search")
        public String search(String body) {
            return "from-search:body=" + body;
        }
    }

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_scatterGather_5legs_happyPath() {
        RestAssured.given()
                .body("hello")
                .post("/scatter")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("from-a|from-b:xyz=123|from-target:body=hello|from-something:body=--- start ---\nhello|from-search:body=hello"));
    }
}
