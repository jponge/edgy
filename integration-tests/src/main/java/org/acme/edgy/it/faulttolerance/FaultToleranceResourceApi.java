package org.acme.edgy.it.faulttolerance;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestResponse;

import io.smallrye.mutiny.Uni;

@Path("/api/fault-tolerance")
class FaultToleranceResourceApi {

    private final AtomicInteger circuitBreakerInvocationCounter = new AtomicInteger();

    @POST
    @Path("/blocking-timeout")
    public RestResponse<Void> blockingTimeout(Long timeout) throws InterruptedException {
        Thread.sleep(timeout);
        return RestResponse.ok();
    }

    @POST
    @Path("/non-blocking-timeout")
    public Uni<RestResponse<Void>> nonBlockingTimeout(Long timeout) {
        return Uni.createFrom().nullItem().onItem().delayIt().by(Duration.ofMillis(timeout))
                .replaceWith(RestResponse.ok());
    }


    @GET
    @Path("/rate-limit")
    public RestResponse<Void> rateLimit() {
        return RestResponse.ok();
    }

    @GET
    @Path("/bulkhead")
    public RestResponse<Void> bulkhead() throws InterruptedException {
        Thread.sleep(1000); // simulate some work
        return RestResponse.ok();
    }

    @GET
    @Path("/circuit-breaker")
    public RestResponse<Void> circuitBreaker() {
        // ==== CB CLOSED ==== (initial state)
        // 1-5 succeeds
        // 6-10 fails
        // ==== CB OPEN ====
        // ... does not increment the counter ...
        // ... after delay period ...
        // ==== CB HALF-OPEN ====
        // 11-12 succeeds
        // 13 fails
        // ==== CB OPEN ====
        // ... does not increment the counter ...
        // ... after delay period ...
        // ==== CB HALF-OPEN ====
        // 14-16 succeeds
        // ==== CB CLOSED ====
        // 16-21 succeeds
        switch (circuitBreakerInvocationCounter.incrementAndGet()) {
            case 6, 7, 8, 9, 10, 13:
                return RestResponse.serverError();
            default:
                return RestResponse.ok();
        }
    }

    @GET
    @Path("/circuit-breaker-reset")
    public RestResponse<Void> circuitBreakerReset() {
        circuitBreakerInvocationCounter.set(0);
        return RestResponse.ok();
    }

    @GET
    @Path("/check-cb-counter")
    public RestResponse<Void> checkCircuitBreakerCounter() {
        if (circuitBreakerInvocationCounter.get() == 21) {
            return RestResponse.ok();
        }
        return RestResponse.serverError();
    }

    @GET
    @Path("/circuit-breaker-with-timeout")
    public RestResponse<Void> circuitBreakerWithTimeout() throws InterruptedException {
        if (circuitBreakerInvocationCounter.incrementAndGet() < 5) {
            Thread.sleep(500); // (limit is 400ms)
        }
        return RestResponse.ok();
    }
}
