package org.acme.edgy.it.faulttolerance;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_GATEWAY;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.REQUEST_TIMEOUT;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.SERVICE_UNAVAILABLE;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.TOO_MANY_REQUESTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class FaultToleranceTest {

    private ExecutorService executorService;

    @BeforeEach
    void setup() {
        this.executorService = Executors.newFixedThreadPool(20);
    }

    @AfterEach
    void tearDown() {
        this.executorService.shutdown();
    }

    @Test
    void testTimeoutSuccess() {
        long timeout = 200L;
        RestAssured.given().body(timeout).when().post("/blocking-timeout").then().statusCode(OK);
    }

    @Test
    void testBlockingTimeoutFailed() {
        long timeout = 450L;
        RestAssured.given().when().body(timeout).post("/blocking-timeout").then().statusCode(REQUEST_TIMEOUT).and()
                .body(Matchers.containsString("timed out"));
    }

    @Test
    void testNonBlockingTimeoutSuccess() {
        long timeout = 450L;
        RestAssured.given().body(timeout).when().post("/non-blocking-timeout").then().statusCode(REQUEST_TIMEOUT).and()
                .body(Matchers.containsString("timed out"));
    }

    @Test
    void testRateLimit() throws InterruptedException, ExecutionException {
        int numberOfRequests = 20;
        int rateLimit = 10;
        long smoothWindowMillis = 1000L;
        long smoothWindowMillisWithOverhead = (long) (smoothWindowMillis * 1.05);

        List<Callable<Integer>> tasks = new ArrayList<>(numberOfRequests);
        for (int i = 0; i < numberOfRequests; i++) {
            tasks.add(() -> RestAssured.given().when().get("/rate-limit").getStatusCode());
        }

        long startTime = System.currentTimeMillis();
        List<Future<Integer>> results = executorService.invokeAll(tasks);
        long duration = System.currentTimeMillis() - startTime;

        int countOfInBoundRequests = 0;
        int countOfRateLimitedRequests = 0;

        for (Future<Integer> result : results) {
            int statusCode = result.get();
            switch (statusCode) {
                case OK -> countOfInBoundRequests++;
                case TOO_MANY_REQUESTS -> countOfRateLimitedRequests++;
                default -> Assertions.fail("Unexpected status code: " + statusCode);
            }
        }

        assertTrue(duration <= smoothWindowMillisWithOverhead,
                "Duration " + duration + "ms exceeded threshold of " + smoothWindowMillisWithOverhead + "ms");
        assertEquals(rateLimit, countOfInBoundRequests, "Successful requests mismatch");
        assertEquals(numberOfRequests - rateLimit, countOfRateLimitedRequests, "Throttled requests mismatch");
    }

    @Test
    void testBulkhead() throws InterruptedException, ExecutionException {
        int bulkheadLimit = 5;
        int queueSize = 3;
        int totalCapacity = bulkheadLimit + queueSize;
        int numberOfRequests = totalCapacity + 1;

        CountDownLatch readyLatch = new CountDownLatch(bulkheadLimit);
        CountDownLatch blockLatch = new CountDownLatch(1);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfRequests; i++) {
            tasks.add(() -> {
                readyLatch.countDown();
                blockLatch.await(5, TimeUnit.SECONDS);
                return RestAssured.given().when().get("/bulkhead").getStatusCode();
            });
        }

        List<Future<Integer>> results = executorService.invokeAll(tasks);
        boolean saturated = readyLatch.await(2, TimeUnit.SECONDS);
        assertTrue(saturated, "Threads failed to start in time");

        blockLatch.countDown();

        int countOfSuccessful = 0;
        int countOfRejected = 0;
        for (Future<Integer> result : results) {
            switch (result.get()) {
                case OK -> countOfSuccessful++;
                case BAD_GATEWAY -> countOfRejected++;
                default -> Assertions.fail("Unexpected status code from bulkhead: " + result.get());
            }
        }

        assertEquals(totalCapacity, countOfSuccessful);
        assertEquals(1, countOfRejected);
    }

    @Test
    void testCircuitBreaker() throws InterruptedException, ExecutionException {
        RestAssured.given().when().get("/api/fault-tolerance/circuit-breaker-reset").then().statusCode(OK);
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

        List<Callable<Integer>> tasks = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> RestAssured.given().when().get("/circuit-breaker").getStatusCode());
        }
        List<Future<Integer>> results = executorService.invokeAll(tasks);
        // two succeds (200)
        // one fail on a server side (502)
        // three fail on a client side (503) => limit reached (successThreshold)
        int countOf200 = 0;
        int countOf502 = 0;
        int countOf503 = 0;
        for (Future<Integer> result : results) {
            int statusCode = result.get();
            switch (statusCode) {
                case OK -> countOf200++;
                case BAD_GATEWAY -> countOf502++;
                case SERVICE_UNAVAILABLE -> countOf503++;
                default -> Assertions.fail("Unexpected status code: " + statusCode);
            }
        }
        // if the first request is the failed one (the first response retrieved by the
        // executor service => is not the first request on backend), then the CB will
        // lazily change its state to OPEN
        assertTrue(countOf200 <= 2);
        assertEquals(countOf502, 1);
        assertEquals(countOf503, 6 - countOf200 - countOf502);

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
        for (Future<Integer> result : results) {
            int statusCode = result.get();
            switch (statusCode) {
                case OK -> countOf200++;
                case SERVICE_UNAVAILABLE -> countOf503++;
                default -> Assertions.fail("Unexpected status code: " + statusCode);
            }
        }
        // the reason for this is that the CB can close quickly before the other three
        // requests have a chance to hit the CB in HALF-OPEN state => they will pass
        assertTrue(countOf200 >= 3 && countOf200 <= 6, "Successful requests mismatch");
        assertEquals(6 - countOf200, countOf503, "Client error requests mismatch");

        // finally verify that the circuit breaker is closed
        // 5 - (countOf200 - 3) is because of the comment above (overflow from the 3
        // requests that were expected to hit HALF-OPEN state but actually hit CLOSED
        // state => hit the backend/counter)
        for (int i = 0; i < 5 - (countOf200 - 3); i++) {
            RestAssured.given().when().get("/circuit-breaker").then().statusCode(OK);
        }

        // check number of invocations on the backend
        RestAssured.given().when().get("/api/fault-tolerance/check-cb-counter").then().statusCode(OK);
    }

    @Test
    void testCircuitBreakerWithTimeout() {
        RestAssured.given().when().get("/api/fault-tolerance/circuit-breaker-reset").then().statusCode(OK);

        for (int i = 0; i < 4; i++) {
            RestAssured.given().when().get("/circuit-breaker-with-timeout").then()
                    .statusCode(REQUEST_TIMEOUT);
        }

        // NOT IN OPEN STATE
        RestAssured.given().when().get("/circuit-breaker-with-timeout").then()
                .statusCode(OK);
    }

    @Test
    void testFallback() {
        RestAssured.given().when().get("/with-fallback").then().statusCode(OK).and()
                .body(Matchers.equalTo("Fallback response"));
    }
}
