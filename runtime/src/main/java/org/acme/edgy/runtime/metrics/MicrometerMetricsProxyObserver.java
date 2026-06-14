package org.acme.edgy.runtime.metrics;

import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.ProxyObservation;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.httpproxy.ProxyContext;

/**
 * {@link ProxyObserver} that records proxy request metrics via Micrometer.
 */
@Singleton
public class MicrometerMetricsProxyObserver implements ProxyObserver {

    private static final String PROXY_REQUESTS = "edgy.proxy.requests";
    private static final String ROUTES_COUNT = "edgy.routes.count";
    private static final String ORIGINS_COUNT = "edgy.origins.count";

    private static final String TAG_METHOD = "method";
    private static final String TAG_ROUTE = "route";
    private static final String TAG_ORIGIN = "origin";
    private static final String TAG_STATUS = "status";
    private static final String TAG_OUTCOME = "outcome";

    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_CLIENT_ERROR = "CLIENT_ERROR";
    private static final String OUTCOME_SERVER_ERROR = "SERVER_ERROR";
    private static final String OUTCOME_PROXY_ERROR = "PROXY_ERROR";

    private final MeterRegistry registry;

    MicrometerMetricsProxyObserver(MeterRegistry registry, RoutingConfiguration routingConfiguration) {
        this.registry = registry;

        int routeCount = routingConfiguration.routes().size();
        long originCount = routingConfiguration.routes().stream()
                .map(r -> r.origin().identifier())
                .distinct()
                .count();

        Gauge.builder(ROUTES_COUNT, () -> routeCount)
                .description("Number of configured proxy routes")
                .register(registry);
        Gauge.builder(ORIGINS_COUNT, () -> originCount)
                .description("Number of unique proxy origins")
                .register(registry);
    }

    @Override
    public ProxyObservation observe(ProxyContext context, Route route) {
        Timer.Sample sample = Timer.start(registry);
        String method = context.request().getMethod().name();

        return new ProxyObservation() {
            @Override
            public void end(ProxyContext context) {
                int status = context.response().getStatusCode();
                sample.stop(timer(route, method, status,
                        StatusCode.isClientError(status) ? OUTCOME_CLIENT_ERROR : OUTCOME_SUCCESS));
            }

            @Override
            public void error(ProxyContext context, Throwable error) {
                int status = context.response().getStatusCode();
                sample.stop(timer(route, method, status,
                        error != null ? OUTCOME_PROXY_ERROR : OUTCOME_SERVER_ERROR));
            }
        };
    }

    private Timer timer(Route route, String method, int status, String outcome) {
        return Timer.builder(PROXY_REQUESTS)
                .tag(TAG_METHOD, method)
                .tag(TAG_ROUTE, route.path())
                .tag(TAG_ORIGIN, route.origin().identifier())
                .tag(TAG_STATUS, String.valueOf(status))
                .tag(TAG_OUTCOME, outcome)
                .register(registry);
    }
}
