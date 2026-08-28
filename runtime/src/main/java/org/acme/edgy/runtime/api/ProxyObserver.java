package org.acme.edgy.runtime.api;

import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.ProxyContext;

/**
 * SPI for observing proxied requests. Implementations are discovered via CDI
 * and invoked by
 * {@link org.acme.edgy.runtime.interceptors.ObservingProxyInterceptor}.
 */
public interface ProxyObserver {

    /**
     * Begins observing a proxied request, returning a handle to track its
     * lifecycle.
     *
     * @param context the proxy context for the incoming request
     * @param route   the matched route for the proxied request
     */
    ProxyObservation observe(ProxyContext context, Route route);

    /**
     * Begins observing a scatter/gather request before legs are dispatched.
     */
    ScatterObservation observeScatter(RoutingContext context, ScatterRoute scatterRoute);
}
