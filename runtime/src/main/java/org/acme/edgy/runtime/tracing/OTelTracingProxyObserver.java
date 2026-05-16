package org.acme.edgy.runtime.tracing;

import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.ProxyObservation;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.Route;
import org.jboss.logging.Logger;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.vertx.httpproxy.ProxyContext;

/**
 * {@link ProxyObserver} that extends the current OTel span with proxy-specific attributes.
 */
@Singleton
public class OTelTracingProxyObserver implements ProxyObserver {

    private static final Logger logger = Logger.getLogger(OTelTracingProxyObserver.class);

    static final AttributeKey<String> EDGY_ORIGIN_URL = AttributeKey.stringKey("edgy.origin.url");
    static final AttributeKey<String> EDGY_ORIGIN_ID = AttributeKey.stringKey("edgy.origin.id");
    static final AttributeKey<String> EDGY_ROUTE = AttributeKey.stringKey("edgy.route");

    @Override
    public ProxyObservation observe(ProxyContext context, Route route) {
        Span span = Span.current();
        if (!span.isRecording()) {
            // e.g. when `quarkus.otel.instrument.vertx-http=false` or OTel SDK is disabled
            logger.warn("No recording span available, skipping edgy tracing attributes");
            return ProxyObservation.NOOP;
        }

        span.setAttribute(EDGY_ORIGIN_URL, route.origin().uri());
        span.setAttribute(EDGY_ORIGIN_ID, route.origin().identifier());
        span.setAttribute(EDGY_ROUTE, route.path());

        return ProxyObservation.NOOP;
    }
}
