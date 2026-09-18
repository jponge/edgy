package org.acme.edgy.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.config.EdgyOriginCacheConfig;
import org.acme.edgy.runtime.config.EdgyOriginConfig;
import org.acme.edgy.runtime.config.EdgyRuntimeConfig;
import org.acme.edgy.runtime.interceptors.CacheInterceptor;

import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.cache.CacheOptions;

/**
 * Owns one response cache per distinct origin URI.
 * <p>
 * Routes pointing at the identical origin target share one cache, distinct
 * targets get their own. Configuration ({@code enabled}, {@code max-size},
 * {@code max-entry-size}) still resolves by origin identifier, so
 * {@code max-size} now applies per distinct origin URI rather than per
 * identifier.
 */
@Singleton
public class OriginCacheManager {

    private final Map<String, CacheInterceptor> interceptors = new HashMap<>();

    private final EdgyRuntimeConfig edgyRuntimeConfig;

    OriginCacheManager(EdgyRuntimeConfig edgyRuntimeConfig) {
        this.edgyRuntimeConfig = edgyRuntimeConfig;
    }

    /**
     * @return the cache interceptor for this origin, or empty when the origin has
     *         no configuration entry or caching is disabled for it
     */
    public Optional<ProxyInterceptor> interceptorFor(Origin origin) {
        EdgyOriginConfig originConfig = edgyRuntimeConfig.origins().get(origin.identifier());
        if (originConfig == null || !originConfig.cache().enabled()) {
            return Optional.empty();
        }
        EdgyOriginCacheConfig cacheConfig = originConfig.cache();
        // populated from configure(@Observes Router), single-threaded at startup
        return Optional.of(interceptors.computeIfAbsent(origin.uri(),
                ignored -> new CacheInterceptor(
                        new CacheOptions().setMaxSize(cacheConfig.maxSize()),
                        cacheConfig.maxEntrySize().asLongValue())));
    }
}
