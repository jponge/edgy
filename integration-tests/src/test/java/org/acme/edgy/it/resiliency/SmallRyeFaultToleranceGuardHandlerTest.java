package org.acme.edgy.it.resiliency;

import static jakarta.ws.rs.core.HttpHeaders.RETRY_AFTER;
import static org.acme.edgy.runtime.api.utils.StatusCode.BAD_GATEWAY;
import static org.acme.edgy.runtime.api.utils.StatusCode.OK;
import static org.acme.edgy.runtime.api.utils.StatusCode.PAYLOAD_TOO_LARGE;
import static org.acme.edgy.runtime.api.utils.StatusCode.SERVICE_UNAVAILABLE;
import static org.acme.edgy.runtime.api.utils.StatusCode.TOO_MANY_REQUESTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@QuarkusTest
class SmallRyeFaultToleranceGuardHandlerTest {

    private ExecutorService executorService;

    @BeforeEach
    void setup() {
        this.executorService = Executors.newFixedThreadPool(20);
    }

    @AfterEach
    void tearDown() {
        this.executorService.shutdown();
    }

    private void resetRetryCounter() {
        RestAssured.given().when().get("/api/resiliency/retry-reset").then().statusCode(OK);
    }

    @Test
    void testRetrySuccess() {
        resetRetryCounter();
        // maxRetries=3, so 2 failures + 1 success
        RestAssured.given()
                .body(2)
                .when().post("/retry")
                .then().statusCode(OK);
    }

    @Test
    void testRetryFailed() {
        resetRetryCounter();
        // maxRetries=3, so after 4 total attempts (1 + 3 retries) all fail
        RestAssured.given()
                .body(10)
                .when().post("/retry")
                .then().statusCode(not(OK));
    }

    @Test
    void testRateLimit() throws InterruptedException, ExecutionException {
        int numberOfRequests = 20;
        int rateLimit = 10;
        long smoothWindowMillis = 1000L;
        long smoothWindowMillisWithOverhead = (long) (smoothWindowMillis * 1.05);

        List<Callable<Response>> tasks = new ArrayList<>(numberOfRequests);
        for (int i = 0; i < numberOfRequests; i++) {
            tasks.add(() -> RestAssured.given().when().get("/rate-limit"));
        }

        long startTime = System.currentTimeMillis();
        List<Future<Response>> results = executorService.invokeAll(tasks);
        long duration = System.currentTimeMillis() - startTime;

        int countOfInBoundRequests = 0;
        int countOfRateLimitedRequests = 0;

        for (Future<Response> result : results) {
            Response response = result.get();
            int statusCode = response.getStatusCode();
            switch (statusCode) {
                case OK -> countOfInBoundRequests++;
                case TOO_MANY_REQUESTS -> {
                    countOfRateLimitedRequests++;
                    String retryAfter = response.getHeader(RETRY_AFTER);
                    assertThat(retryAfter)
                            .as("RETRY_AFTER header missing for TOO_MANY_REQUESTS response")
                            .isNotNull().isNotEmpty();
                    assertThat(retryAfter)
                            .as("RETRY_AFTER header should be 1 second")
                            .isEqualTo("1");
                }
                default -> fail("Unexpected status code: " + statusCode);
            }
        }

        assertThat(duration)
                .as("Duration %dms exceeded threshold of %dms", duration, smoothWindowMillisWithOverhead)
                .isLessThanOrEqualTo(smoothWindowMillisWithOverhead);
        assertThat(countOfInBoundRequests).as("Successful requests mismatch").isEqualTo(rateLimit);
        assertThat(countOfRateLimitedRequests).as("Throttled requests mismatch")
                .isEqualTo(numberOfRequests - rateLimit);
    }

    @Test
    void testBulkhead() throws InterruptedException, ExecutionException {
        int bulkheadLimit = 5;
        int queueSize = 3;
        int totalCapacity = bulkheadLimit + queueSize;
        int numberOfRequests = totalCapacity + 1;

        CountDownLatch readyLatch = new CountDownLatch(bulkheadLimit);
        CountDownLatch blockLatch = new CountDownLatch(1);

        List<Callable<Response>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfRequests; i++) {
            tasks.add(() -> {
                readyLatch.countDown();
                blockLatch.await(5, TimeUnit.SECONDS);
                return RestAssured.given().when().get("/bulkhead");
            });
        }

        List<Future<Response>> results = executorService.invokeAll(tasks);
        boolean saturated = readyLatch.await(2, TimeUnit.SECONDS);
        assertThat(saturated).as("Threads failed to start in time").isTrue();

        blockLatch.countDown();

        int countOfSuccessful = 0;
        int countOfRejected = 0;
        for (Future<Response> result : results) {
            int statusCode = result.get().getStatusCode();
            switch (statusCode) {
                case OK -> countOfSuccessful++;
                case BAD_GATEWAY -> countOfRejected++;
                default -> fail("Unexpected status code from bulkhead: " + statusCode);
            }
        }

        assertThat(countOfSuccessful).isEqualTo(totalCapacity);
        assertThat(countOfRejected).isEqualTo(1);
    }

    @Test
    void testCircuitBreaker() throws InterruptedException, ExecutionException {
        RestAssured.given().when().get("/api/resiliency/circuit-breaker-reset").then().statusCode(OK);
        // requestVolumeThreshold := 10
        // failureRatio := 0.5
        // successThreshold := 3
        long circuitBreakerDelaySeconds = 1;

        // First 5 requests succeeds
        for (int i = 0; i < 5; i++) {
            RestAssured.given().when().get("/circuit-breaker").then().statusCode(OK);
        }

        // Last 5 requests fail
        for (int i = 0; i < 5; i++) {
            RestAssured.given().when().get("/circuit-breaker").then().statusCode(BAD_GATEWAY);
        }

        // because failure ratio is 50%, circuit breaker should be in OPEN state
        // for `circuitBreakerDelaySeconds` seconds
        // Next five requests should fail fast on a client side without hitting the
        // backend
        for (int i = 0; i < 5; i++) {
            RestAssured.given().when().get("/circuit-breaker").then().statusCode(SERVICE_UNAVAILABLE);
        }

        // wait until circuit breaker transitions to HALF-OPEN state
        Thread.sleep(circuitBreakerDelaySeconds * 1000);

        List<Callable<Response>> tasks = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> RestAssured.given().when().get("/circuit-breaker"));
        }
        List<Future<Response>> results = executorService.invokeAll(tasks);
        // two succeds (StatusCode.OK)
        // one fail on a server side (StatusCode.BAD_GATEWAY)
        // three fail on a client side (StatusCode.SERVICE_UNAVAILABLE) => limit reached
        // (successThreshold)
        int countOf200 = 0;
        int countOf502 = 0;
        int countOf503 = 0;
        for (Future<Response> result : results) {
            int statusCode = result.get().getStatusCode();
            switch (statusCode) {
                case OK -> countOf200++;
                case BAD_GATEWAY -> countOf502++;
                case SERVICE_UNAVAILABLE -> countOf503++;
                default -> fail("Unexpected status code: " + statusCode);
            }
        }
        // if the first request is the failed one (the first response retrieved by the
        // executor service => is not the first request on backend), then the CB will
        // lazily change its state to OPEN
        assertThat(countOf200).isLessThanOrEqualTo(2);
        assertThat(countOf502).isEqualTo(1);
        assertThat(countOf503).isEqualTo(6 - countOf200 - countOf502);

        // because the success threshold failed to be reached, the circuit breaker
        // should be in OPEN state
        for (int i = 0; i < 5; i++) {
            RestAssured.given().when().get("/circuit-breaker").then().statusCode(SERVICE_UNAVAILABLE);
        }

        // wait until circuit breaker transitions to HALF-OPEN state
        Thread.sleep(circuitBreakerDelaySeconds * 1000);
        // next three requests should succeed and close the circuit breaker
        results = executorService.invokeAll(tasks);
        countOf200 = 0;
        countOf503 = 0;
        for (Future<Response> result : results) {
            int statusCode = result.get().getStatusCode();
            switch (statusCode) {
                case OK -> countOf200++;
                case SERVICE_UNAVAILABLE -> countOf503++;
                default -> fail("Unexpected status code: " + statusCode);
            }
        }
        // the reason for this is that the CB can close quickly before the other three
        // requests have a chance to hit the CB in HALF-OPEN state => they will pass
        assertThat(countOf200).as("Successful requests mismatch")
                .isGreaterThanOrEqualTo(3).isLessThanOrEqualTo(6);
        assertThat(countOf503).as("Client error requests mismatch").isEqualTo(6 - countOf200);

        // finally verify that the circuit breaker is closed
        // 5 - (countOf200 - 3) is because of the comment above (overflow from the 3
        // requests that were expected to hit HALF-OPEN state but actually hit CLOSED
        // state => hit the backend/counter)
        for (int i = 0; i < 5 - (countOf200 - 3); i++) {
            RestAssured.given().when().get("/circuit-breaker").then().statusCode(OK);
        }

        // check number of invocations on the backend
        RestAssured.given().when().get("/api/resiliency/check-cb-counter").then().statusCode(OK);
    }

    @Test
    void testCircuitBreakerWithRateLimit() {
        // rate limit: 1 request per 1000ms rolling window
        // CB: requestVolumeThreshold=4, failureRatio=0.5, skipOn=RateLimitException

        // first request succeeds
        RestAssured.given().when().get("/circuit-breaker-with-rate-limit").then().statusCode(OK);

        // next 3 requests are rate-limited
        for (int i = 0; i < 3; i++) {
            RestAssured.given().when().get("/circuit-breaker-with-rate-limit").then()
                    .statusCode(TOO_MANY_REQUESTS);
        }

        // CB should NOT be in OPEN state because RateLimitException is skipped
        // wait for rate limit window to fully reset
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        RestAssured.given().when().get("/circuit-breaker-with-rate-limit").then().statusCode(OK);
    }

    @Test
    void testFallback() {
        RestAssured.given().when().get("/with-fallback").then().statusCode(OK).and()
                .body(Matchers.equalTo("Fallback response"));
    }

    @Test
    void testRateLimitWithFallback() {
        // rate limit: 1 request per 1000ms fixed window
        // first request succeeds normally
        RestAssured.given().when().get("/rate-limit-with-fallback").then().statusCode(OK);

        // second request hits rate limit but fallback catches it
        RestAssured.given().when().get("/rate-limit-with-fallback").then().statusCode(OK).and()
                .body(Matchers.equalTo("Rate limit fallback"));
    }

    @Test
    void testCircuitBreakerWithFallback() {
        // CB: requestVolumeThreshold=4, failureRatio=0.5, delay=10s
        // backend always returns 500, expectation is SC_SUCCESS so all count as failures
        // first 4 requests fail at backend — CB monitors but stays closed
        for (int i = 0; i < 4; i++) {
            RestAssured.given().when().get("/circuit-breaker-with-fallback").then().statusCode(OK).and()
                    .body(Matchers.equalTo("Circuit breaker fallback"));
        }

        // CB should now be open (4 requests, all failures, ratio = 1.0 > 0.5)
        // next request is rejected by CB, fallback catches it
        RestAssured.given().when().get("/circuit-breaker-with-fallback").then().statusCode(OK).and()
                .body(Matchers.equalTo("Circuit breaker fallback"));
    }

    @Test
    void testExpectationWithFallback() {
        // no guard handler — just expectation + fallback
        // backend always returns 500, SC_SUCCESS expectation fails, fallback kicks in
        RestAssured.given().when().get("/expectation-with-fallback").then().statusCode(OK).and()
                .body(Matchers.equalTo("Expectation fallback"));
    }

    @Test
    void testRetryWithFallback() {
        RestAssured.given().when().get("/api/resiliency/retry-fallback-reset").then().statusCode(OK);

        // retry: maxRetries=2 (3 total attempts), backend always returns 500
        // all attempts fail, fallback catches the final failure
        RestAssured.given().when().get("/retry-with-fallback").then().statusCode(OK).and()
                .body(Matchers.equalTo("Retry fallback"));

        // verify backend was hit exactly 3 times (1 initial + 2 retries)
        String counter = RestAssured.given().when().get("/api/resiliency/retry-fallback-counter")
                .then().statusCode(OK).extract().body().asString();
        assertThat(counter).as("Backend should be hit 3 times (1 initial + 2 retries)").isEqualTo("3");
    }

    @Test
    void testPayloadWithinLimit() {
        String smallBody = "hello";
        RestAssured.given()
                .body(smallBody)
                .when().post("/payload-limit")
                .then().statusCode(OK)
                .body(Matchers.equalTo(smallBody));
    }

    @Test
    void testPayloadExceedsLimit() {
        String largeBody = "x".repeat(100);
        RestAssured.given()
                .body(largeBody)
                .when().post("/payload-limit")
                .then().statusCode(PAYLOAD_TOO_LARGE);
    }

    // ----------------------------- PASSTHROUGH -----------------------------

    @Test
    void testGuardPassthrough() {
        RestAssured.given()
                .when().get("/guard-passthrough")
                .then().statusCode(OK);
    }

    // ----------------------------- SCATTER GUARD -----------------------------

    @Test
    void testScatterGuardFallbackOnLeg() {
        // First call: CB monitors, backend returns 500, fallback activates
        RestAssured.given()
                .get("/scatter-guard")
                .then()
                .statusCode(OK);

        // Second call: CB opens, fallback activates
        RestAssured.given()
                .get("/scatter-guard")
                .then()
                .statusCode(OK)
                .body(is("healthy|guard-fallback"));
    }

    @Test
    void testScatterGuardOnOneLegWithFallback() {
        RestAssured.given()
                .get("/scatter-guard-fallback")
                .then()
                .statusCode(OK)
                .body(is("healthy|guard-fallback"));
    }

    @Test
    void testScatterGuardWithPayloadLimit() {
        RestAssured.given()
                .body("x".repeat(17))
                .post("/scatter-payload-limit")
                .then()
                .statusCode(BAD_GATEWAY);
    }

    @Test
    void testScatterPayloadWithinLimit() {
        String smallBody = "hello";
        RestAssured.given()
                .body(smallBody)
                .post("/scatter-payload-ok")
                .then()
                .statusCode(OK)
                .body(is(smallBody + "|healthy"));
    }

    @Test
    void testScatterGuardOnAllLegsWithFallback() {
        RestAssured.given()
                .get("/scatter-guard-all-legs")
                .then()
                .statusCode(OK)
                .body(is("fallback-1|fallback-2"));
    }

    @Test
    void testScatterGuardPassthrough() {
        RestAssured.given()
                .get("/scatter-guard-passthrough")
                .then()
                .statusCode(OK)
                .body(is("healthy|healthy"));
    }

    @Test
    void testScatterRetryOnLeg() {
        RestAssured.given().when().get("/api/scatter/retry-reset").then().statusCode(OK);

        // retry leg: fails 2 times, then succeeds on 3rd attempt (maxRetries=3)
        RestAssured.given()
                .get("/scatter-guard-retry")
                .then()
                .statusCode(OK)
                .body(is("healthy|retry-ok"));
    }

    @Test
    void testScatterCircuitBreakerNoFallbackFails() {
        // CB on one leg, no fallback, FAIL_FAST — backend returns 500,
        // expectation fails, CB records failure, no fallback → leg fails → 502
        RestAssured.given()
                .get("/scatter-cb-no-fallback")
                .then()
                .statusCode(BAD_GATEWAY);
    }

    @Test
    void testScatterRateLimitOnLeg() {
        // rate limit: 1 request per 1000ms fixed window
        // first request succeeds
        RestAssured.given()
                .get("/scatter-rate-limit")
                .then()
                .statusCode(OK)
                .body(is("healthy|healthy"));

        // second request: rate-limited leg fails, but PARTIAL mode delivers to composer
        RestAssured.given()
                .get("/scatter-rate-limit")
                .then()
                .statusCode(OK);
    }

    @Test
    void testScatterRateLimitWithFallbackOnLeg() {
        // rate limit: 1 request per 1000ms fixed window + fallback
        // first request succeeds
        RestAssured.given()
                .get("/scatter-rate-limit-fallback")
                .then()
                .statusCode(OK)
                .body(is("healthy|healthy"));

        // second request: rate-limited leg triggers fallback
        RestAssured.given()
                .get("/scatter-rate-limit-fallback")
                .then()
                .statusCode(OK)
                .body(is("healthy|rate-limit-fallback"));
    }

    @Test
    void testScatterRetryExhaustedNoFallbackFails() {
        // retry on one leg (maxRetries=2), backend always fails, no fallback
        // FAIL_FAST → entire scatter fails → 502
        RestAssured.given()
                .get("/scatter-retry-fail")
                .then()
                .statusCode(BAD_GATEWAY);
    }
}
