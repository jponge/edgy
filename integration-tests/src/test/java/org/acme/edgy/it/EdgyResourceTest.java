package org.acme.edgy.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class EdgyResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/edgy")
                .then()
                .statusCode(200)
                .body(is("Hello edgy"));
    }
}
