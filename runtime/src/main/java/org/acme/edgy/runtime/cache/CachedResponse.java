package org.acme.edgy.runtime.cache;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyResponse;

/**
 * A single origin response held in an origin's cache.
 * <p>
 * The current time is always passed in rather than read from the system clock,
 * so freshness is deterministic and testable.
 */
public final class CachedResponse {

    private final int statusCode;
    private final String statusMessage;
    private final MultiMap headers;
    private final Buffer content;
    private final long timestampMillis;
    private final long freshnessLifetimeMillis;

    /**
     * @param statusCode the origin status code
     * @param statusMessage the origin status message
     * @param headers the origin response headers, copied by the caller
     * @param content the fully read response body
     * @param timestampMillis when the response was stored
     * @param freshnessLifetimeMillis how long the response stays fresh
     */
    public CachedResponse(int statusCode, String statusMessage, MultiMap headers,
            Buffer content, long timestampMillis, long freshnessLifetimeMillis) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers;
        this.content = content;
        this.timestampMillis = timestampMillis;
        this.freshnessLifetimeMillis = freshnessLifetimeMillis;
    }

    /**
     * Whether the freshness lifetime has elapsed at {@code nowMillis}.
     */
    public boolean isExpired(long nowMillis) {
        return nowMillis >= timestampMillis + freshnessLifetimeMillis;
    }

    /**
     * How long this response has been held, in whole seconds, for the
     * {@code Age} header. Never negative.
     */
    public long ageSeconds(long nowMillis) {
        return Math.max(0, (nowMillis - timestampMillis) / 1000);
    }

    /**
     * How long this response has been held, in milliseconds. Never negative.
     */
    public long ageMillis(long nowMillis) {
        return Math.max(0, nowMillis - timestampMillis);
    }

    /**
     * Restores this response onto {@code response}, including an {@code Age}
     * header reflecting the time spent in the cache.
     */
    public void applyTo(ProxyResponse response, long nowMillis) {
        response.setStatusCode(statusCode);
        response.setStatusMessage(statusMessage);
        response.headers().addAll(headers);
        response.headers().set(HttpHeaders.AGE, Long.toString(ageSeconds(nowMillis)));
        response.setBody(Body.body(content));
    }
}
