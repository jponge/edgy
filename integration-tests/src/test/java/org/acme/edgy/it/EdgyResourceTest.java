package org.acme.edgy.it;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

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
