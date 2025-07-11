package org.acme.edgy.test;

import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

import static org.hamcrest.Matchers.is;

public class EdgyTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

//    @Test
//    public void writeYourOwnUnitTest() {
//        // Write your unit tests here - see the testing extension guide https://quarkus.io/guides/writing-extensions#testing-extensions for more information
//        Assertions.assertTrue(true, "Add some assertions to " + getClass().getName());
//    }

    @Test
    public void smokeTest_configurator() {
        // TODO eventually drop this smoke test
        RestAssured.given()
                .get("/yolo")
                .then()
                .statusCode(200)
                .body(is("Yolo!"));
    }
}
