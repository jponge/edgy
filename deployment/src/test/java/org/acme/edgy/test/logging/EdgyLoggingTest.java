package org.acme.edgy.test.logging;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

class EdgyLoggingTest {

    private static final String HELLO_ORIGIN_ID = "origin-1";
    private static final String HELLO_ROUTE_PATH = "/hello";
    private static final String UNREACHABLE_ORIGIN_ID = "unreachable-origin";
    private static final String UNREACHABLE_ROUTE_PATH = "/unreachable";

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class))
            .overrideConfigKey("edgy.logging.enabled", "true")
            .setLogRecordPredicate(rec -> rec.getLoggerName().contains("LoggingProxyObserver"))
            .assertLogRecords(records -> {
                assertTrue(records.size() >= 2,
                        "Expected at least 2 log records from LoggingProxyObserver");

                var successRec = records.stream()
                        .filter(r -> String.format(r.getMessage(), r.getParameters()).contains("status=200"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No INFO log with status=200 found"));
                String successMsg = String.format(successRec.getMessage(), successRec.getParameters());
                assertTrue(successMsg.contains(HELLO_ROUTE_PATH), "Log should contain route path");
                assertTrue(successMsg.contains(HELLO_ORIGIN_ID), "Log should contain origin identifier");
                assertTrue(successMsg.contains("ms"), "Log should contain duration");

                var errorRec = records.stream()
                        .filter(r -> String.format(r.getMessage(), r.getParameters()).contains("status=502"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No WARN log with status=502 found"));
                assertTrue(errorRec.getLevel().intValue() >= Level.WARNING.intValue(),
                        "5xx responses should be logged at WARN, but was: " + errorRec.getLevel());
                String errorMsg = String.format(errorRec.getMessage(), errorRec.getParameters());
                assertTrue(errorMsg.contains(UNREACHABLE_ROUTE_PATH), "Log should contain route path");
                assertTrue(errorMsg.contains(UNREACHABLE_ORIGIN_ID), "Log should contain origin identifier");
                assertTrue(errorMsg.contains("ms"), "Log should contain duration");
            });

    @Test
    void test_loggingEmitsInfoForProxiedRequest() {
        given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello from origin!"));
    }

    @Test
    void test_loggingEmitsWarnForServerError() {
        given()
                .get("/unreachable")
                .then()
                .statusCode(StatusCode.BAD_GATEWAY);
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route(HELLO_ROUTE_PATH,
                            Origin.of(HELLO_ORIGIN_ID, "http://localhost:8081/test/hello")))
                    .addRoute(new Route(UNREACHABLE_ROUTE_PATH,
                            Origin.of(UNREACHABLE_ORIGIN_ID, "http://localhost:1/nowhere"))).build();
        }
    }

    @Path("/test/hello")
    static class TestApi {

        @GET
        public String hello() {
            return "Hello from origin!";
        }
    }
}
