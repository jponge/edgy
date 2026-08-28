package org.acme.edgy.runtime.metrics;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.LegResponse;
import org.acme.edgy.runtime.api.ProxyObservation;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.RoutingEntry;
import org.acme.edgy.runtime.api.ScatterObservation;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.ProxyContext;

/**
 * {@link ProxyObserver} that records proxy request metrics via Micrometer.
 */
@Singleton
public class MicrometerMetricsProxyObserver implements ProxyObserver {

    private static final String PROXY_REQUESTS = "edgy.proxy.requests";
    private static final String SCATTER_REQUESTS = "edgy.scatter.requests";
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
    private static final String OUTCOME_PARTIAL = "PARTIAL";
    private static final String OUTCOME_ERROR = "ERROR";

    private final MeterRegistry registry;

    MicrometerMetricsProxyObserver(MeterRegistry registry, RoutingConfiguration routingConfiguration) {
        this.registry = registry;

        int routeCount = routingConfiguration.entries().size();
        long originCount = routingConfiguration.entries().stream()
                .flatMap(MicrometerMetricsProxyObserver::originIdentifiers)
                .distinct()
                .count();

        Gauge.builder(ROUTES_COUNT, () -> routeCount)
                .description("Number of configured proxy routes")
                .register(registry);
        Gauge.builder(ORIGINS_COUNT, () -> originCount)
                .description("Number of unique proxy origins")
                .register(registry);
    }

    private static Stream<String> originIdentifiers(RoutingEntry entry) {
        if (entry instanceof Route route) {
            return Stream.of(route.origin().identifier());
        } else if (entry instanceof ScatterRoute scatter) {
            return scatter.legs().stream().map(leg -> leg.origin().identifier());
        }
        return Stream.empty();
    }

    @Override
    public ProxyObservation observe(ProxyContext context, Route route) {
        Timer.Sample sample = Timer.start(registry);
        String method = context.request().getMethod().name();

        return new ProxyObservation() {
            @Override
            public void end(ProxyContext context) {
                int status = context.response().getStatusCode();
                sample.stop(proxyTimer(route, method, status,
                        StatusCode.isClientError(status) ? OUTCOME_CLIENT_ERROR : OUTCOME_SUCCESS));
            }

            @Override
            public void error(ProxyContext context, Throwable error) {
                int status = context.response().getStatusCode();
                sample.stop(proxyTimer(route, method, status,
                        error != null ? OUTCOME_PROXY_ERROR : OUTCOME_SERVER_ERROR));
            }
        };
    }

    @Override
    public ScatterObservation observeScatter(RoutingContext context, ScatterRoute scatterRoute) {
        Timer.Sample sample = Timer.start(registry);

        return new ScatterObservation() {
            @Override
            public Future<LegResponse> wrapLeg(Leg leg, Supplier<Future<LegResponse>> execution) {
                return execution.get();
            }

            @Override
            public void end(List<LegResponse> responses) {
                boolean allSucceeded = responses.stream().allMatch(LegResponse::succeeded);
                sample.stop(scatterTimer(scatterRoute, allSucceeded ? OUTCOME_SUCCESS : OUTCOME_PARTIAL));
            }

            @Override
            public void error(Throwable error) {
                sample.stop(scatterTimer(scatterRoute, OUTCOME_ERROR));
            }
        };
    }

    private Timer proxyTimer(Route route, String method, int status, String outcome) {
        return Timer.builder(PROXY_REQUESTS)
                .tag(TAG_METHOD, method)
                .tag(TAG_ROUTE, route.path())
                .tag(TAG_ORIGIN, route.origin().identifier())
                .tag(TAG_STATUS, String.valueOf(status))
                .tag(TAG_OUTCOME, outcome)
                .register(registry);
    }

    private Timer scatterTimer(ScatterRoute scatterRoute, String outcome) {
        return Timer.builder(SCATTER_REQUESTS)
                .tag(TAG_ROUTE, scatterRoute.path())
                .tag(TAG_OUTCOME, outcome)
                .register(registry);
    }
}
