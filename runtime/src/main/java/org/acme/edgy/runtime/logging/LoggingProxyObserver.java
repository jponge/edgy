package org.acme.edgy.runtime.logging;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
import org.jboss.logging.Logger;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.ProxyContext;

/**
 * {@link ProxyObserver} that logs proxied request lifecycle events.
 */
@Singleton
public class LoggingProxyObserver implements ProxyObserver {

    private static final Logger logger = Logger.getLogger(LoggingProxyObserver.class);

    LoggingProxyObserver(RoutingConfiguration routingConfiguration) {
        for (RoutingEntry entry : routingConfiguration.entries()) {
            if (entry instanceof Route route) {
                logger.infof("Configured route %s -> %s | %s",
                        route.path(), route.origin().identifier(), route.origin().uri());
            } else if (entry instanceof ScatterRoute scatter) {
                scatter.legs().forEach(leg -> logger.infof("Configured scatter route %s -> %s | %s",
                        scatter.path(), leg.origin().identifier(), leg.origin().uri()));
            }
        }
    }

    @Override
    public ProxyObservation observe(ProxyContext context, Route route) {
        long startNanos = System.nanoTime();

        return new ProxyObservation() {
            @Override
            public void end(ProxyContext context) {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                int statusCode = context.response().getStatusCode();
                logger.infof("Proxied %s -> %s | %s | status=%d | %dms",
                        route.path(), route.origin().identifier(), route.origin().uri(),
                        statusCode, durationMs);
            }

            @Override
            public void error(ProxyContext context, Throwable error) {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                if (error != null) {
                    logger.warnf("Proxied %s -> %s | %s | error: %s: %s | %dms",
                            route.path(), route.origin().identifier(), route.origin().uri(),
                            error.getClass().getSimpleName(), error.getMessage(), durationMs);
                    logger.debugf(error, "Full stacktrace for proxied %s -> %s",
                            route.path(), route.origin().identifier());
                    return;
                }
                int statusCode = context.response().getStatusCode();
                logger.warnf("Proxied %s -> %s | %s | status=%d | %dms",
                        route.path(), route.origin().identifier(), route.origin().uri(),
                        statusCode, durationMs);
            }
        };
    }

    @Override
    public ScatterObservation observeScatter(RoutingContext context, ScatterRoute scatterRoute) {
        long startNanos = System.nanoTime();
        logger.infof("Scatter %s | %d legs | dispatching",
                scatterRoute.path(), scatterRoute.legs().size());

        return new ScatterObservation() {
            @Override
            public Future<LegResponse> wrapLeg(Leg leg, Supplier<Future<LegResponse>> execution) {
                return execution.get();
            }

            @Override
            public void end(List<LegResponse> responses) {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                long succeeded = responses.stream().filter(LegResponse::succeeded).count();
                logger.infof("Scatter %s | %d/%d legs succeeded | %dms",
                        scatterRoute.path(), succeeded, responses.size(), durationMs);
            }

            @Override
            public void error(Throwable error) {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                logger.warnf("Scatter %s | error: %s: %s | %dms",
                        scatterRoute.path(),
                        error.getClass().getSimpleName(), error.getMessage(), durationMs);
                logger.debugf(error, "Full stacktrace for scatter %s", scatterRoute.path());
            }
        };
    }
}
