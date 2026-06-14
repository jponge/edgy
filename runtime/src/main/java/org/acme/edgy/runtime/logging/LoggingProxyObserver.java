package org.acme.edgy.runtime.logging;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.ProxyObservation;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.jboss.logging.Logger;

import io.vertx.httpproxy.ProxyContext;

/**
 * {@link ProxyObserver} that logs proxied request lifecycle events.
 */
@Singleton
public class LoggingProxyObserver implements ProxyObserver {

    private static final Logger logger = Logger.getLogger(LoggingProxyObserver.class);

    LoggingProxyObserver(RoutingConfiguration routingConfiguration) {
        for (Route route : routingConfiguration.routes()) {
            logger.infof("Configured route %s -> %s | %s",
                    route.path(), route.origin().identifier(), route.origin().uri());
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
}
