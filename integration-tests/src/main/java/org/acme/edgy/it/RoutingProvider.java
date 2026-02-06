package org.acme.edgy.it;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.REQUEST_TIMEOUT;

import java.time.temporal.ChronoUnit;
import java.util.function.Function;

import jakarta.enterprise.inject.Produces;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.builtins.requests.RequestFaultToleranceApplier;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import io.smallrye.faulttolerance.api.RateLimitType;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyResponse;

class RoutingProvider {

    @Produces
    RoutingConfiguration routing() {
        return new RoutingConfigurationBuilder(new RoutingConfiguration())
                .addRoutes(this::storkRoutes)
                .addRoutes(this::faultToleranceRoutes)
                .build();
    }

    private RoutingConfiguration storkRoutes(RoutingConfiguration routingConfiguration) {
        return routingConfiguration.addRoute(
                new Route("/test", Origin.of("stork-origin", "stork://my-service/test/hello"), PathMode.FIXED));
    }

    private RoutingConfiguration faultToleranceRoutes(RoutingConfiguration routingConfiguration) {
        // Timeout Configuration
        long timeoutLimitMillis = 400L;
        // Rate Limit Configuration
        int rateLimit = 10;
        long windowMillis = 1000L;
        // Bulkhead Configuration
        int bulkheadLimit = 5;
        int queueSize = 3;
        // Circuit Breaker Configuration
        double failureRatio = 0.5;
        int requestVolumeThreshold = 10;
        int delaySeconds = 1;
        int successThreshold = 3;

        return routingConfiguration
                // ----------------------------- TIMEOUT ROUTES -----------------------------
                .addRoute(new Route("/blocking-timeout",
                        Origin.of("blocking-timeout-origin",
                                "http://localhost:8081/api/fault-tolerance/blocking-timeout"),
                        PathMode.FIXED)
                        .addRequestTransformer(
                                new RequestFaultToleranceApplier(
                                        builder -> builder.withTimeout().duration(timeoutLimitMillis, ChronoUnit.MILLIS)
                                                .done())))
                .addRoute(new Route("/non-blocking-timeout",
                        Origin.of("non-blocking-timeout-origin",
                                "http://localhost:8081/api/fault-tolerance/non-blocking-timeout"),
                        PathMode.FIXED)
                        .addRequestTransformer(
                                new RequestFaultToleranceApplier(
                                        builder -> builder.withTimeout().duration(timeoutLimitMillis, ChronoUnit.MILLIS)
                                                .done())))
                // ----------------------------- RATE LIMIT ROUTES -----------------------------
                .addRoute(new Route("/rate-limit",
                        Origin.of("rate-limit-origin",
                                        "http://localhost:8081/api/fault-tolerance/rate-limit"),
                        PathMode.FIXED)
                        .addRequestTransformer(
                                new RequestFaultToleranceApplier(
                                        builder -> builder.withRateLimit().limit(rateLimit)
                                                .type(RateLimitType.ROLLING)
                                                .window(windowMillis, ChronoUnit.MILLIS)
                                                .done())))
                // ----------------------------- BULKHEAD -----------------------------
                .addRoute(new Route("/bulkhead",
                        Origin.of("bulkhead-origin",
                                        "http://localhost:8081/api/fault-tolerance/bulkhead"),
                        PathMode.FIXED)
                        .addRequestTransformer(
                                new RequestFaultToleranceApplier(
                                        builder -> builder.withBulkhead()
                                                .limit(bulkheadLimit)
                                                .queueSize(queueSize)
                                                .done())))
                // ----------------------------- CIRCUIT BREAKER -----------------------------
                .addRoute(new Route("/circuit-breaker",
                        Origin.of("circuit-breaker-origin",
                                "http://localhost:8081/api/fault-tolerance/circuit-breaker"),
                        PathMode.FIXED)
                        .addRequestTransformer(
                                new RequestFaultToleranceApplier(
                                        builder -> builder.withCircuitBreaker()
                                                .requestVolumeThreshold(requestVolumeThreshold)
                                                .failureRatio(failureRatio)
                                                .successThreshold(successThreshold)
                                                .delay(delaySeconds, ChronoUnit.SECONDS)
                                                .done(),
                                        response -> response.getStatusCode() / 100 == 2)))
                // ---------------- CB + TIMEOUT (skipOn TimeoutException) ---------------------
                .addRoute(new Route("/circuit-breaker-with-timeout",
                        Origin.of("circuit-breaker-with-timeout-origin",
                                        "http://localhost:8081/api/fault-tolerance/circuit-breaker-with-timeout"),
                        PathMode.FIXED)
                        .addRequestTransformer(
                                new RequestFaultToleranceApplier(
                                        builder -> builder
                                                .withTimeout().duration(timeoutLimitMillis, ChronoUnit.MILLIS).done()
                                                .withCircuitBreaker()
                                                .requestVolumeThreshold(4)
                                                .failureRatio(failureRatio)
                                                .skipOn(TimeoutException.class) // !!! IMPORTANT !!!
                                                .done(),
                                        response -> response.getStatusCode() / 100 <= 3
                                                || response.getStatusCode() == REQUEST_TIMEOUT)))

                // ----------------------------- FALLBACK -----------------------------
                .addRoute(new Route("/with-fallback",
                        Origin.of("with-fallback-origin",
                                "http://localhost:8081/api/non-existing-endpoint"),
                        PathMode.FIXED)
                        .addRequestTransformer(
                                new RequestFaultToleranceApplier((proxyContext, builder) -> builder
                                        .withFallback()
                                        .handler(() -> {
                                            ProxyResponse fallbackResponse = proxyContext.request().release()
                                                    .response()
                                                    .setBody(Body.body(Buffer.buffer("Fallback response")))
                                                    .setStatusCode(OK);
                                            return Future.succeededFuture(fallbackResponse);
                                        })
                                        .done())));
    };

    // for clear structure of tested routes
    static class RoutingConfigurationBuilder {
        private RoutingConfiguration routingConfiguration;

        RoutingConfigurationBuilder(RoutingConfiguration routingConfiguration) {
            this.routingConfiguration = routingConfiguration;
        }

        RoutingConfigurationBuilder addRoutes(
                Function<RoutingConfiguration, RoutingConfiguration> routingConfiguration) {
            routingConfiguration.apply(this.routingConfiguration);
            return this;
        }

        RoutingConfiguration build() {
            return routingConfiguration;
        }

    }
}
