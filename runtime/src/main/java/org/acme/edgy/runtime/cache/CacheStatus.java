package org.acme.edgy.runtime.cache;

/**
 * Whether a proxied request was served from an origin's cache.
 * <p>
 * Published in the {@link io.vertx.httpproxy.ProxyContext} under
 * {@link #CONTEXT_KEY} and read by
 * {@link org.acme.edgy.runtime.api.ProxyObserver} implementations. Absent when
 * the origin has no cache, or when the request method is not cacheable.
 */
public enum CacheStatus {

    /** The response was replayed from the cache and the origin was not called. */
    HIT,

    /** The cache was consulted and the request went on to the origin. */
    MISS;

    /** The {@code ProxyContext} attribute holding a {@link CacheStatus}. */
    public static final String CONTEXT_KEY = "edgy.cache.status";
}
