package org.acme.edgy.it;

import static org.acme.edgy.runtime.api.utils.StatusCode.OK;

import java.time.temporal.ChronoUnit;
import java.util.function.Function;

import jakarta.enterprise.inject.Produces;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;

import io.smallrye.faulttolerance.api.RateLimitException;
import io.smallrye.faulttolerance.api.RateLimitType;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyResponse;

class RoutingProvider {

    @Produces
    RoutingConfiguration routing() {
        return new RoutingConfigurationBuilder(RoutingConfiguration.builder())
                .addRoutes(this::storkRoutes)
                .addRoutes(this::resiliencyRoutes)
                .build();
    }

    private RoutingConfiguration.Builder storkRoutes(RoutingConfiguration.Builder builder) {
        return builder
                .addRoute(new Route("/test", Origin.of("stork-origin", "stork://my-service/test/hello")))
                .addRoute(new Route("/test-secured",
                        Origin.of("secured-stork-origin", "storks://my-secured-service/test/hello")));
    }

    private RoutingConfiguration.Builder resiliencyRoutes(RoutingConfiguration.Builder builder) {
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

        return builder
                // ----------------------------- RATE LIMIT ROUTES -----------------------------
                .addRoute(new Route("/rate-limit",
                        Origin.of("rate-limit-origin",
                                "http://localhost:8081/api/resiliency/rate-limit")
                                .guard(adapter -> adapter.withRateLimit().limit(rateLimit)
                                        .type(RateLimitType.ROLLING)
                                        .window(windowMillis, ChronoUnit.MILLIS)
                                        .done())))
                // ----------------------------- BULKHEAD -----------------------------
                .addRoute(new Route("/bulkhead",
                        Origin.of("bulkhead-origin",
                                "http://localhost:8081/api/resiliency/bulkhead")
                                .guard(adapter -> adapter.withBulkhead()
                                        .limit(bulkheadLimit)
                                        .queueSize(queueSize)
                                        .done())))
                // ----------------------------- CIRCUIT BREAKER -----------------------------
                .addRoute(new Route("/circuit-breaker",
                        Origin.of("circuit-breaker-origin",
                                "http://localhost:8081/api/resiliency/circuit-breaker")
                                .guard(adapter -> adapter.withCircuitBreaker()
                                        .requestVolumeThreshold(requestVolumeThreshold)
                                        .failureRatio(failureRatio)
                                        .successThreshold(successThreshold)
                                        .delay(delaySeconds, ChronoUnit.SECONDS)
                                        .done(),
                                        StatusCode.SC_SUCCESS)))
                // ---------------- CB + RATE LIMIT (skipOn RateLimitException) ---------------------
                .addRoute(new Route("/circuit-breaker-with-rate-limit",
                        Origin.of("circuit-breaker-with-rate-limit-origin",
                                "http://localhost:8081/api/resiliency/rate-limit")
                                .guard(adapter -> {
                                    adapter.withRateLimit()
                                            .limit(1)
                                            .window(windowMillis, ChronoUnit.MILLIS)
                                            .type(RateLimitType.FIXED)
                                            .done();
                                    adapter.withCircuitBreaker()
                                            .requestVolumeThreshold(4)
                                            .failureRatio(failureRatio)
                                            .skipOn(RateLimitException.class)
                                            .done();
                                }, StatusCode.SC_SUCCESS)))
                // ----------------------------- RETRY -----------------------------
                .addRoute(new Route("/retry",
                        Origin.of("retry-origin",
                                "http://localhost:8081/api/resiliency/retry")
                                .guard(adapter -> adapter.withRetry()
                                        .maxRetries(3)
                                        .delay(0, ChronoUnit.MILLIS)
                                        .jitter(0, ChronoUnit.MILLIS)
                                        .done(),
                                        StatusCode.SC_SUCCESS)))
                // ----------------------------- FALLBACK -----------------------------
                .addRoute(new Route("/with-fallback",
                        Origin.of("with-fallback-origin",
                                "http://localhost:8081/api/non-existing-endpoint")
                                .guard((proxyContext, adapter) -> adapter
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
        private final RoutingConfiguration.Builder builder;

        RoutingConfigurationBuilder(RoutingConfiguration.Builder builder) {
            this.builder = builder;
        }

        RoutingConfigurationBuilder addRoutes(
                Function<RoutingConfiguration.Builder, RoutingConfiguration.Builder> routeFunction) {
            routeFunction.apply(this.builder);
            return this;
        }

        RoutingConfiguration build() {
            return builder.build();
        }
    }
}
