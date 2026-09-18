package org.acme.edgy.runtime.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;

class CachedResponseTest {

    private static CachedResponse entry(long timestamp, long lifetime) {
        return new CachedResponse(200, "OK", MultiMap.caseInsensitiveMultiMap(),
                Buffer.buffer("body"), timestamp, lifetime);
    }

    @Test
    void isFreshStrictlyBeforeTheLifetimeElapses() {
        CachedResponse cached = entry(1_000, 60_000);

        assertThat(cached.isExpired(1_000)).isFalse();
        assertThat(cached.isExpired(60_999)).isFalse();
    }

    @Test
    void isExpiredOnceTheLifetimeElapses() {
        CachedResponse cached = entry(1_000, 60_000);

        assertThat(cached.isExpired(61_000)).isTrue();
        assertThat(cached.isExpired(99_000)).isTrue();
    }

    @Test
    void ageIsWholeSecondsSinceStorage() {
        CachedResponse cached = entry(1_000, 60_000);

        assertThat(cached.ageSeconds(1_000)).isZero();
        assertThat(cached.ageSeconds(2_500)).isEqualTo(1);
        assertThat(cached.ageSeconds(31_000)).isEqualTo(30);
    }

    @Test
    void ageIsNeverNegativeWhenTheClockGoesBackwards() {
        CachedResponse cached = entry(10_000, 60_000);

        assertThat(cached.ageSeconds(5_000)).isZero();
    }
}
