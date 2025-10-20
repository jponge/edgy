package org.acme.edgy.runtime.api.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.ws.rs.core.UriBuilder;

public interface QueryParamUtils {
    static final String EMPTY_QUERY_VALUE = "";
    static final String INVALID_ENCODED_SPACE_REGEX = "\\+";
    static final String NORMALIZED_ENCODED_SPACE = "%20";
    static final String QUERY_SYMBOL = "?";
    static final String QUERY_SEPARATOR_SYMBOL = "&";
    static final String QUERY_VALUE_SEPARATOR_SYMBOL = "=";

    static String normalizeEncodedSpaces(String encodedValue) {
        return encodedValue.replaceAll(INVALID_ENCODED_SPACE_REGEX, NORMALIZED_ENCODED_SPACE);
    }

    static String urlEncode(String value) {
        return normalizeEncodedSpaces(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    static String appendUriQueries(String uri, String encodedQuery) {
        UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        for (String encodedQueryParam : encodedQuery.split(QUERY_SEPARATOR_SYMBOL)) {
            String[] keyValue = encodedQueryParam.split(QUERY_VALUE_SEPARATOR_SYMBOL, 2);
            String key = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : EMPTY_QUERY_VALUE;
            uriBuilder.queryParam(key, value);
        }
        return uriBuilder.build().toString();
    }

    static Set<String> extractEncodedQueryNames(String uri) {
        UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        String rawQuery = uriBuilder.build().getRawQuery();
        if (rawQuery == null) {
            return Set.of();
        }
        return Arrays.stream(rawQuery.split(QUERY_SEPARATOR_SYMBOL))
                .map(queryParam -> queryParam.split(QUERY_VALUE_SEPARATOR_SYMBOL, 2)[0])
                .collect(Collectors.toSet());
    }

    static boolean hasQuery(String uri) {
        return uri != null && uri.contains(QUERY_SYMBOL);
    }
}
