package org.acme.edgy.it.stork;

import static io.restassured.RestAssured.given;
import static org.acme.edgy.it.stork.StorkResourceApi.FIRST_SECURED_SERVICE_PORT;
import static org.acme.edgy.it.stork.StorkResourceApi.FIRST_SERVICE_PORT;
import static org.acme.edgy.it.stork.StorkResourceApi.SECOND_SECURED_SERVICE_PORT;
import static org.acme.edgy.it.stork.StorkResourceApi.SECOND_SERVICE_PORT;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;

// order of the annotations is important
@Certificates(baseDir = "target/certs", certificates = @Certificate(name = "edgy", password = "password", formats = Format.PKCS12, client = true))
@QuarkusTest
@WithTestResource(ConsulTestResource.class)
class EdgyStorkConsulTest {

    List<String> expectedBodies = Stream.of(FIRST_SERVICE_PORT, SECOND_SERVICE_PORT).map(String::valueOf).toList();
    List<String> expectedSecuredBodies = Stream.of(FIRST_SECURED_SERVICE_PORT, SECOND_SECURED_SERVICE_PORT)
            .map(String::valueOf).toList();

    static {
        // 8081 => testing port, this is only for the static setUp method, without it
        // would send requests to 8080 port,
        // which is not where the application is running in QuarkusTest, but
        // assertTestEndpointAndGetBody seem to
        // work with the correct port (8081) even without it...
        RestAssured.port = 8081;
    }

    @BeforeAll
    static void setUp() {
        // starts services
        given()
                .when().get("/api/stork/services")
                .then()
                .statusCode(OK);

        // starts secured services
        given()
                .when().get("/api/stork/secured-services")
                .then()
                .statusCode(OK);

        // registers services in consul
        given()
                .when().get("/api/stork/consul")
                .then()
                .statusCode(OK);
    }

    @Test
    void testStorkLoadBalancingRouting() {
        String firstCallBody = assertTestEndpointAndGetBody("/test");
        int firstOrSecondIndex = (expectedBodies.indexOf(firstCallBody) + 1) % 2;
        for (int i = firstOrSecondIndex; i < 9 + firstOrSecondIndex; i++) {
            assertEquals(expectedBodies.get(i % 2), assertTestEndpointAndGetBody("/test"));
        }
    }

    @Test
    void testStorkSecuredLoadBalancingRouting() {
        String firstCallBody = assertTestEndpointAndGetBody("/test-secured");
        int firstOrSecondIndex = (expectedSecuredBodies.indexOf(firstCallBody) + 1) % 2;
        for (int i = firstOrSecondIndex; i < 9 + firstOrSecondIndex; i++) {
            assertEquals(expectedSecuredBodies.get(i % 2), assertTestEndpointAndGetBody("/test-secured"));
        }
    }

    private String assertTestEndpointAndGetBody(String path) {
        return given()
                .when().get(path)
                .then()
                .statusCode(OK)
                .extract().body().asString();
    }

}
