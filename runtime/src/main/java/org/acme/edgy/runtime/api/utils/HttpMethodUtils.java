package org.acme.edgy.runtime.api.utils;

import java.util.Set;

import io.vertx.core.http.HttpMethod;

public final class HttpMethodUtils {

    private static final Set<HttpMethod> BODYLESS_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.DELETE,
            HttpMethod.OPTIONS, HttpMethod.TRACE);

    private HttpMethodUtils() {
    }

    /**
     * Per RFC 9110: GET, HEAD, DELETE, OPTIONS, and TRACE have no defined
     * request body semantics. POST, PUT, and PATCH do.
     */
    public static boolean hasRequestBodySemantics(HttpMethod method) {
        return !BODYLESS_METHODS.contains(method);
    }
}
