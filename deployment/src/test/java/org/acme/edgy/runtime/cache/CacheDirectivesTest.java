package org.acme.edgy.runtime.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.vertx.core.MultiMap;

class CacheDirectivesTest {

    @Test
    void nullHeaderYieldsNoDirectives() {
        CacheDirectives directives = CacheDirectives.parse(null);

        assertThat(directives.isPublic()).isFalse();
        assertThat(directives.noStore()).isFalse();
        assertThat(directives.noCache()).isFalse();
        assertThat(directives.freshnessLifetimeMillis()).isEqualTo(-1);
    }

    @Test
    void parsesPublicAndMaxAgeAsMilliseconds() {
        CacheDirectives directives = CacheDirectives.parse("public, max-age=60");

        assertThat(directives.isPublic()).isTrue();
        assertThat(directives.maxAgeMillis()).isEqualTo(60_000);
        assertThat(directives.freshnessLifetimeMillis()).isEqualTo(60_000);
    }

    @Test
    void sharedMaxAgeTakesPrecedenceOverMaxAge() {
        CacheDirectives directives = CacheDirectives.parse("public, max-age=60, s-maxage=30");

        assertThat(directives.freshnessLifetimeMillis()).isEqualTo(30_000);
    }

    @Test
    void parsingIsCaseInsensitiveAndIgnoresSurroundingSpace() {
        CacheDirectives directives = CacheDirectives.parse("  Public ,  Max-Age=15 ");

        assertThat(directives.isPublic()).isTrue();
        assertThat(directives.freshnessLifetimeMillis()).isEqualTo(15_000);
    }

    @Test
    void parsesNoStoreNoCacheAndPrivate() {
        assertThat(CacheDirectives.parse("no-store").noStore()).isTrue();
        assertThat(CacheDirectives.parse("no-cache").noCache()).isTrue();
        assertThat(CacheDirectives.parse("private").isPublic()).isFalse();
    }

    @Test
    void malformedAgeIsIgnoredRatherThanThrowing() {
        CacheDirectives directives = CacheDirectives.parse("public, max-age=abc");

        assertThat(directives.isPublic()).isTrue();
        assertThat(directives.freshnessLifetimeMillis()).isEqualTo(-1);
    }

    @Test
    void unknownDirectivesAreIgnored() {
        CacheDirectives directives = CacheDirectives.parse("public, immutable, max-age=5");

        assertThat(directives.freshnessLifetimeMillis()).isEqualTo(5_000);
    }

    @Test
    void fallsBackToExpiresMinusDateWhenNoAgeDirective() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .set("Cache-Control", "public")
                .set("Date", "Tue, 15 Nov 1994 08:12:31 GMT")
                .set("Expires", "Tue, 15 Nov 1994 08:13:31 GMT");

        assertThat(CacheDirectives.freshnessLifetimeMillis(headers)).isEqualTo(60_000);
    }

    @Test
    void directiveAgeWinsOverExpiresHeaders() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .set("Cache-Control", "public, max-age=10")
                .set("Date", "Tue, 15 Nov 1994 08:12:31 GMT")
                .set("Expires", "Tue, 15 Nov 1994 08:13:31 GMT");

        assertThat(CacheDirectives.freshnessLifetimeMillis(headers)).isEqualTo(10_000);
    }

    @Test
    void unparseableDatesFallBackToAbsent() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .set("Cache-Control", "public")
                .set("Date", "not-a-date")
                .set("Expires", "also-not-a-date");

        assertThat(CacheDirectives.freshnessLifetimeMillis(headers)).isEqualTo(-1);
    }
}
