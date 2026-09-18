package org.acme.edgy.runtime.interceptors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.acme.edgy.runtime.builtins.transformers.BodyAccumulator;
import org.acme.edgy.runtime.cache.CacheDirectives;
import org.acme.edgy.runtime.cache.CachePolicy;
import org.acme.edgy.runtime.cache.CacheStatus;
import org.acme.edgy.runtime.cache.CachedResponse;
import org.acme.edgy.runtime.cache.VariantKey;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;

/**
 * Serves origin responses from a per-origin cache.
 * <p>
 * Placed after the observing and method interceptors so cache hits are still
 * observed and the effective request method is visible, and before the URI
 * rewrite. Works unchanged on 1:1 routes and scatter legs, because both drive
 * interceptors through the same cursor protocol.
 * <p>
 * A hit short-circuits the chain at this interceptor, so everything inner to it
 * is skipped on both legs. Response transformers therefore do not re-run - the
 * stored body is the transformed one, since transformers are innermost and ran
 * before the entry was written. The guard interceptor is skipped too, which is
 * intended: it protects the origin, and a hit never reaches the origin.
 * <p>
 * Each request records a {@link CacheStatus} in the context for observers.
 * <p>
 * Eviction is FIFO: the backing store drops the oldest inserted entry once
 * {@code max-size} is exceeded.
 */
public final class CacheInterceptor implements ProxyInterceptor {

    private static final String REQUEST_HEADERS_KEY = "edgy.cache.request-headers";

    private final Map<String, CachedResponse> responses;
    private final Map<String, List<String>> varyFields;
    private final long maxEntrySize;

    /**
     * @param options sizing for both backing stores
     * @param maxEntrySize the largest body this cache will hold
     */
    public CacheInterceptor(CacheOptions options, long maxEntrySize) {
        Cache<String, CachedResponse> responseCache = options.newCache();
        Cache<String, List<String>> varyCache = options.newCache();
        // CacheImpl is a LinkedHashMap, and one origin's cache is reached from
        // every event loop serving it
        this.responses = Collections.synchronizedMap(responseCache);
        this.varyFields = Collections.synchronizedMap(varyCache);
        this.maxEntrySize = maxEntrySize;
    }

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        ProxyRequest request = context.request();
        HttpMethod method = request.getMethod();
        if (method != HttpMethod.GET) {
            return context.sendRequest();
        }

        // snapshot before inner request transformers run, so the store path
        // selects Vary values from the same headers the lookup used
        MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap().addAll(request.headers());
        context.set(REQUEST_HEADERS_KEY, requestHeaders);
        context.set(CacheStatus.CONTEXT_KEY, CacheStatus.MISS);

        CacheDirectives directives = CacheDirectives.parse(
                requestHeaders.get(HttpHeaders.CACHE_CONTROL));
        if (directives.noStore() || directives.noCache()) {
            return context.sendRequest();
        }

        long now = System.currentTimeMillis();
        String key = lookupKey(request.absoluteURI(), requestHeaders);
        CachedResponse cached = responses.computeIfPresent(key,
                (ignored, entry) -> entry.isExpired(now) ? null : entry);
        if (cached == null) {
            return context.sendRequest();
        }

        long requestMaxAge = directives.maxAgeMillis();
        if (requestMaxAge != CacheDirectives.ABSENT
                && cached.ageMillis(now) > requestMaxAge) {
            return context.sendRequest();
        }

        request.release();
        ProxyResponse response = request.response();
        cached.applyTo(response, now);
        context.set(CacheStatus.CONTEXT_KEY, CacheStatus.HIT);
        return Future.succeededFuture(response);
    }

    @Override
    public Future<Void> handleProxyResponse(ProxyContext context) {
        ProxyResponse response = context.response();

        // the unwind re-enters the interceptor that short-circuited, and the
        // replayed entry looks storable - re-storing it would refresh its
        // timestamp and defeat expiry
        if (context.get(CacheStatus.CONTEXT_KEY, CacheStatus.class) == CacheStatus.HIT) {
            return context.sendResponse();
        }

        MultiMap requestHeaders = context.get(REQUEST_HEADERS_KEY, MultiMap.class);
        if (requestHeaders == null) {
            return context.sendResponse();
        }

        ProxyRequest request = response.request();
        String uri = request.absoluteURI();
        HttpMethod method = request.getMethod();

        CacheDirectives directives = CacheDirectives.parse(
                requestHeaders.get(HttpHeaders.CACHE_CONTROL));
        Body body = response.getBody();
        if (body == null
                || !CachePolicy.isStorable(method, response.getStatusCode(), response.headers(), directives)) {
            return context.sendResponse();
        }

        // accumulating past the limit fails the promise, and a failed promise
        // here never reaches sendResponse() - the request would 502. Caching
        // must not change a response that proxies fine, so a body too large to
        // hold, or of unknown length, is streamed uncached. Content-Length is
        // the only size known up front; a chunked response is never stored.
        long length = body.length();
        if (length < 0 || length > maxEntrySize) {
            return context.sendResponse();
        }

        List<String> fields = VariantKey.fields(response.headers().get(HttpHeaders.VARY));
        String key = VariantKey.compose(uri, fields, requestHeaders);

        // the cache's own limit, not quarkus.http.limits.max-body-size, which
        // bounds inbound request bodies and may be configured smaller
        return BodyAccumulator.readBodyBuffer(body, maxEntrySize).compose(buffer -> {
            response.setBody(Body.body(buffer));
            CachedResponse entry = new CachedResponse(
                    response.getStatusCode(),
                    response.getStatusMessage(),
                    MultiMap.caseInsensitiveMultiMap().addAll(response.headers()),
                    buffer,
                    System.currentTimeMillis(),
                    CacheDirectives.freshnessLifetimeMillis(response.headers()));
            return context.sendResponse().onSuccess(sent -> {
                if (!fields.isEmpty()) {
                    varyFields.put(uri, fields);
                } else {
                    varyFields.remove(uri);
                }
                responses.put(key, entry);
            });
        });
    }

    // a dropped varyFields entry makes this fall back to the bare URI and miss,
    // orphaning the variant until it is evicted too - never a wrong hit
    private String lookupKey(String uri, MultiMap requestHeaders) {
        List<String> fields = varyFields.get(uri);
        return fields == null ? uri : VariantKey.compose(uri, fields, requestHeaders);
    }
}
