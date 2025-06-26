package org.acme.edgy.test;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.hamcrest.MatcherAssert;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

import java.util.ArrayList;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EdgyTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("application.properties")
                    .addClass(TestEndpoint.class));

    @Test
    public void check_endpoint_is_deployed() {
        given()
                .when()
                .get("/yolo")
                .then()
                .statusCode(is(200))
                .body(is("Yolo!"));
    }

    @Test
    public void check_config() {
        JsonPath jsonPath = given()
                .when()
                .get("/config")
                .then()
                .statusCode(is(200))
                .extract().body().jsonPath();
        assertThat(jsonPath.getString("host"), is("localhost"));
        assertThat(jsonPath.getString("port"), is("1234"));
        assertThat(jsonPath.getString("foo.bar"), is("Bar"));
        assertThat(jsonPath.getString("flexes.first.id"), is("the-first"));
        assertThat(jsonPath.getString("flexes.first.extra.def"), is("456"));
    }
}
