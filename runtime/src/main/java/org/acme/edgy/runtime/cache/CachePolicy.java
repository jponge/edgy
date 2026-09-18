package org.acme.edgy.runtime.cache;

import java.util.Set;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

/**
 * Decides whether an origin response may be stored.
 */
public final class CachePolicy {

    /**
     * Status codes edgy is willing to cache.
     * <p>
     * {@code 206} is excluded because replaying partial content requires
     * {@code Range} handling that edgy does not implement.
     */
    private static final Set<Integer> CACHEABLE_STATUS_CODES = Set.of(
            200, 203, 300, 301, 308, 404, 405, 410, 414, 501);

    private CachePolicy() {
    }

    /**
     * Whether a response may be stored.
     *
     * @param method the effective request method sent to the origin
     * @param statusCode the origin status code
     * @param responseHeaders the origin response headers
     * @param requestDirectives the request's {@code Cache-Control} directives
     * @return {@code true} when every storability condition holds
     */
    public static boolean isStorable(HttpMethod method, int statusCode,
            MultiMap responseHeaders, CacheDirectives requestDirectives) {
        if (method != HttpMethod.GET || requestDirectives.noStore()) {
            return false;
        }
        if (!CACHEABLE_STATUS_CODES.contains(statusCode)) {
            return false;
        }
        if (VariantKey.isWildcard(responseHeaders.get(HttpHeaders.VARY))) {
            return false;
        }
        if (responseHeaders.contains(HttpHeaders.SET_COOKIE)) {
            return false;
        }
        CacheDirectives responseDirectives = CacheDirectives.parse(
                responseHeaders.get(HttpHeaders.CACHE_CONTROL));
        if (!responseDirectives.isPublic()) {
            return false;
        }
        return CacheDirectives.freshnessLifetimeMillis(responseHeaders) > 0;
    }
}
