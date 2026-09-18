package org.acme.edgy.runtime.cache;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

import io.vertx.core.MultiMap;

/**
 * Cache key arithmetic for the {@code Vary} response header.
 * <p>
 * A response that varies on request headers cannot be keyed on the URI alone:
 * {@code Vary: Accept-Encoding} means a body stored for a client that accepts
 * gzip must not be replayed to one that does not. The selected header values
 * therefore become part of the key.
 */
public final class VariantKey {

    private static final String WILDCARD = "*";
    private static final String SEPARATOR = "\n";

    private VariantKey() {
    }

    /**
     * Whether the {@code Vary} header is {@code *}, meaning the response
     * varies on unspecified request attributes and must never be cached.
     */
    public static boolean isWildcard(String varyHeader) {
        return varyHeader != null && varyHeader.trim().equals(WILDCARD);
    }

    /**
     * Parses a {@code Vary} header into lowercase field names, sorted and
     * deduplicated so the resulting key is stable across requests.
     *
     * @param varyHeader the raw header value, may be {@code null}
     * @return the field names, empty when the response does not vary
     */
    public static List<String> fields(String varyHeader) {
        if (varyHeader == null || varyHeader.isBlank()) {
            return List.of();
        }
        return Arrays.stream(varyHeader.split(","))
                .map(field -> field.trim().toLowerCase())
                .filter(field -> !field.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Builds the cache key for a request.
     *
     * @param uri the inbound absolute URI
     * @param fields the {@code Vary} field names, from {@link #fields(String)}
     * @param requestHeaders the request headers to select values from
     * @return {@code uri} when there are no fields, otherwise a composite key
     */
    public static String compose(String uri, List<String> fields, MultiMap requestHeaders) {
        if (fields.isEmpty()) {
            return uri;
        }
        StringJoiner joiner = new StringJoiner(SEPARATOR);
        joiner.add(uri);
        for (String field : fields) {
            String value = requestHeaders.get(field);
            joiner.add(field + "=" + (value == null ? "" : value));
        }
        return joiner.toString();
    }
}
