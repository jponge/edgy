package org.acme.edgy.it.resiliency;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/resiliency")
class ResiliencyResourceApi {

    private final AtomicInteger retryCounter = new AtomicInteger();
    private final AtomicInteger retryFallbackCounter = new AtomicInteger();
    private final AtomicInteger circuitBreakerInvocationCounter = new AtomicInteger();

    @POST
    @Path("/retry")
    public RestResponse<Void> retry(Long failCount) {
        if (retryCounter.incrementAndGet() <= failCount) {
            return RestResponse.serverError();
        }
        return RestResponse.ok();
    }

    @GET
    @Path("/retry-reset")
    public RestResponse<Void> retryReset() {
        retryCounter.set(0);
        return RestResponse.ok();
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
    @Path("/circuit-breaker-fallback")
    public RestResponse<Void> circuitBreakerFallback() {
        return RestResponse.serverError();
    }

    @GET
    @Path("/retry-fallback")
    public RestResponse<Void> retryFallback() {
        retryFallbackCounter.incrementAndGet();
        return RestResponse.serverError();
    }

    @GET
    @Path("/retry-fallback-reset")
    public RestResponse<Void> retryFallbackReset() {
        retryFallbackCounter.set(0);
        return RestResponse.ok();
    }

    @GET
    @Path("/retry-fallback-counter")
    public RestResponse<Integer> retryFallbackCounterValue() {
        return RestResponse.ok(retryFallbackCounter.get());
    }

    @POST
    @Path("/echo")
    public String echo(String body) {
        return body;
    }
}
