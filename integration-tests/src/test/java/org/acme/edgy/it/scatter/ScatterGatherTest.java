package org.acme.edgy.it.scatter;

import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class ScatterGatherTest {

    @Test
    void test_scatterGather_composesResponses() {
        RestAssured.given()
                .get("/scatter")
                .then()
                .statusCode(OK)
                .body(is("alpha+beta"));
    }
}
