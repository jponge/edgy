package org.acme.edgy.runtime.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;

class CachePolicyTest {

    private static final CacheDirectives NO_REQUEST_DIRECTIVES = CacheDirectives.none();

    private static MultiMap cacheable() {
        return MultiMap.caseInsensitiveMultiMap().set("Cache-Control", "public, max-age=60");
    }

    private static boolean storable(HttpMethod method, int statusCode, MultiMap headers) {
        return CachePolicy.isStorable(method, statusCode, headers, NO_REQUEST_DIRECTIVES);
    }

    @Test
    void storesPublicGetResponsesWithAPositiveLifetime() {
        assertThat(storable(HttpMethod.GET, 200, cacheable())).isTrue();
    }

    @Test
    void doesNotStoreNonGetMethods() {
        assertThat(storable(HttpMethod.POST, 200, cacheable())).isFalse();
        assertThat(storable(HttpMethod.HEAD, 200, cacheable())).isFalse();
        assertThat(storable(HttpMethod.DELETE, 200, cacheable())).isFalse();
    }

    @Test
    void requiresThePublicDirective() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap().set("Cache-Control", "max-age=60");

        assertThat(storable(HttpMethod.GET, 200, headers)).isFalse();
    }

    @Test
    void requiresAPositiveFreshnessLifetime() {
        MultiMap noAge = MultiMap.caseInsensitiveMultiMap().set("Cache-Control", "public");
        MultiMap zeroAge = MultiMap.caseInsensitiveMultiMap().set("Cache-Control", "public, max-age=0");

        assertThat(storable(HttpMethod.GET, 200, noAge)).isFalse();
        assertThat(storable(HttpMethod.GET, 200, zeroAge)).isFalse();
    }

    @Test
    void doesNotStoreResponsesWithoutCacheControl() {
        assertThat(storable(HttpMethod.GET, 200, MultiMap.caseInsensitiveMultiMap())).isFalse();
    }

    @Test
    void storesOnlyAllowlistedStatusCodes() {
        assertThat(storable(HttpMethod.GET, 301, cacheable())).isTrue();
        assertThat(storable(HttpMethod.GET, 404, cacheable())).isTrue();

        assertThat(storable(HttpMethod.GET, 500, cacheable())).isFalse();
        assertThat(storable(HttpMethod.GET, 502, cacheable())).isFalse();
        assertThat(storable(HttpMethod.GET, 302, cacheable())).isFalse();
    }

    @Test
    void doesNotStorePartialContent() {
        assertThat(storable(HttpMethod.GET, 206, cacheable())).isFalse();
    }

    @Test
    void doesNotStoreWhenTheResponseVariesOnEverything() {
        MultiMap headers = cacheable().set("Vary", "*");

        assertThat(storable(HttpMethod.GET, 200, headers)).isFalse();
    }

    @Test
    void storesWhenTheResponseVariesOnNamedHeaders() {
        MultiMap headers = cacheable().set("Vary", "Accept-Encoding");

        assertThat(storable(HttpMethod.GET, 200, headers)).isTrue();
    }

    @Test
    void doesNotStoreWhenTheRequestSaidNoStore() {
        CacheDirectives requestDirectives = CacheDirectives.parse("no-store");

        assertThat(CachePolicy.isStorable(HttpMethod.GET, 200, cacheable(), requestDirectives)).isFalse();
    }

    @Test
    void stillStoresWhenTheRequestSaidNoCache() {
        CacheDirectives requestDirectives = CacheDirectives.parse("no-cache");

        assertThat(CachePolicy.isStorable(HttpMethod.GET, 200, cacheable(), requestDirectives)).isTrue();
    }
}
