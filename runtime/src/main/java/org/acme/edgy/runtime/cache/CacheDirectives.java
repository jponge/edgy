package org.acme.edgy.runtime.cache;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;

/**
 * The directives of a single {@code Cache-Control} header value.
 * <p>
 * Used for both request and response headers. Unknown directives are ignored,
 * and a malformed age (for example {@code max-age=abc}) is treated as absent
 * rather than raising an error.
 * <p>
 * All durations are milliseconds, and {@value #ABSENT} means the directive was
 * not present.
 */
public final class CacheDirectives {

    /** Returned by the duration accessors when a directive is absent. */
    public static final long ABSENT = -1;

    private static final CacheDirectives NONE = new CacheDirectives(false, false, false, ABSENT, ABSENT);

    private static final String PUBLIC = "public";
    private static final String NO_STORE = "no-store";
    private static final String NO_CACHE = "no-cache";
    private static final String MAX_AGE = "max-age=";
    private static final String SHARED_MAX_AGE = "s-maxage=";

    private final boolean isPublic;
    private final boolean noStore;
    private final boolean noCache;
    private final long maxAgeMillis;
    private final long sharedMaxAgeMillis;

    private CacheDirectives(boolean isPublic, boolean noStore, boolean noCache,
            long maxAgeMillis, long sharedMaxAgeMillis) {
        this.isPublic = isPublic;
        this.noStore = noStore;
        this.noCache = noCache;
        this.maxAgeMillis = maxAgeMillis;
        this.sharedMaxAgeMillis = sharedMaxAgeMillis;
    }

    /**
     * Directives for a message that carries no {@code Cache-Control} header.
     */
    public static CacheDirectives none() {
        return NONE;
    }

    /**
     * Parses a {@code Cache-Control} header value.
     *
     * @param headerValue the raw header value, may be {@code null}
     * @return the parsed directives, never {@code null}
     */
    public static CacheDirectives parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return NONE;
        }

        boolean isPublic = false;
        boolean noStore = false;
        boolean noCache = false;
        long maxAge = ABSENT;
        long sharedMaxAge = ABSENT;

        for (String token : headerValue.split(",")) {
            String directive = token.trim().toLowerCase();
            if (directive.equals(PUBLIC)) {
                isPublic = true;
            } else if (directive.equals(NO_STORE)) {
                noStore = true;
            } else if (directive.equals(NO_CACHE)) {
                noCache = true;
            } else if (directive.startsWith(MAX_AGE)) {
                maxAge = parseSeconds(directive.substring(MAX_AGE.length()));
            } else if (directive.startsWith(SHARED_MAX_AGE)) {
                sharedMaxAge = parseSeconds(directive.substring(SHARED_MAX_AGE.length()));
            }
        }

        return new CacheDirectives(isPublic, noStore, noCache, maxAge, sharedMaxAge);
    }

    /**
     * Computes how long a response stays fresh, preferring the
     * {@code Cache-Control} directives and falling back to
     * {@code Expires} minus {@code Date}.
     *
     * @param responseHeaders the origin response headers
     * @return the freshness lifetime in milliseconds, or {@link #ABSENT}
     */
    public static long freshnessLifetimeMillis(MultiMap responseHeaders) {
        CacheDirectives directives = parse(responseHeaders.get(HttpHeaders.CACHE_CONTROL));
        long lifetime = directives.freshnessLifetimeMillis();
        if (lifetime != ABSENT) {
            return lifetime;
        }

        Long date = parseHttpDate(responseHeaders.get(HttpHeaders.DATE));
        Long expires = parseHttpDate(responseHeaders.get(HttpHeaders.EXPIRES));
        if (date == null || expires == null) {
            return ABSENT;
        }
        return expires - date;
    }

    /** Whether the {@code public} directive is present. */
    public boolean isPublic() {
        return isPublic;
    }

    /** Whether the {@code no-store} directive is present. */
    public boolean noStore() {
        return noStore;
    }

    /** Whether the {@code no-cache} directive is present. */
    public boolean noCache() {
        return noCache;
    }

    /** The {@code max-age} directive in milliseconds, or {@link #ABSENT}. */
    public long maxAgeMillis() {
        return maxAgeMillis;
    }

    /**
     * The freshness lifetime in milliseconds, or {@link #ABSENT}.
     * <p>
     * Edgy is a shared cache, so {@code s-maxage} takes precedence over
     * {@code max-age} when both are present.
     */
    public long freshnessLifetimeMillis() {
        return sharedMaxAgeMillis != ABSENT ? sharedMaxAgeMillis : maxAgeMillis;
    }

    private static long parseSeconds(String value) {
        try {
            return Long.parseLong(value.trim()) * 1000;
        } catch (NumberFormatException e) {
            return ABSENT;
        }
    }

    private static Long parseHttpDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
