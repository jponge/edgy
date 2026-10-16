package org.acme.edgy.runtime.api.utils;

import java.util.Set;

import io.vertx.core.Expectation;
import io.vertx.httpproxy.ProxyResponse;

public final class StatusCode {

    enum StatusCodeClass {
        INFORMATIONAL(1),
        SUCCESS(2),
        REDIRECTION(3),
        CLIENT_ERROR(4),
        SERVER_ERROR(5);

        public final int family;

        private StatusCodeClass(int family) {
            this.family = family;
        }
    }

    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int NON_AUTHORITATIVE_INFORMATION = 203;

    public static final int MULTIPLE_CHOICES = 300;
    public static final int MOVED_PERMANENTLY = 301;
    public static final int PERMANENT_REDIRECT = 308;

    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int GONE = 410;
    public static final int PAYLOAD_TOO_LARGE = 413;
    public static final int URI_TOO_LONG = 414;
    public static final int TOO_MANY_REQUESTS = 429;

    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int NOT_IMPLEMENTED = 501;
    public static final int BAD_GATEWAY = 502;
    public static final int SERVICE_UNAVAILABLE = 503;

    public static final Expectation<ProxyResponse> SC_SUCCESS = response -> isSuccess(response.getStatusCode());
    public static final Expectation<ProxyResponse> SC_NON_ERROR = response -> !isError(response.getStatusCode());
    public static final Expectation<ProxyResponse> SC_NON_SERVER_ERROR = response -> !isServerError(response.getStatusCode());

    // 206 is excluded because replaying partial content requires
    // Range handling that edgy does not implement.
    private static final Set<Integer> CACHEABLE_STATUS_CODES = Set.of(
            OK,
            NON_AUTHORITATIVE_INFORMATION,
            MULTIPLE_CHOICES,
            MOVED_PERMANENTLY,
            PERMANENT_REDIRECT,
            NOT_FOUND,
            METHOD_NOT_ALLOWED,
            GONE,
            URI_TOO_LONG,
            NOT_IMPLEMENTED);

    private StatusCode() {
    }

    public static boolean isSuccess(int statusCode) {
        return isStatusCodeInClass(statusCode, StatusCodeClass.SUCCESS);
    }

    public static boolean isClientError(int statusCode) {
        return isStatusCodeInClass(statusCode, StatusCodeClass.CLIENT_ERROR);
    }

    public static boolean isServerError(int statusCode) {
        return isStatusCodeInClass(statusCode, StatusCodeClass.SERVER_ERROR);
    }

    public static boolean isError(int statusCode) {
        return isClientError(statusCode) || isServerError(statusCode);
    }

    public static boolean isCacheable(int statusCode) {
        return CACHEABLE_STATUS_CODES.contains(statusCode);
    }

    private static boolean isStatusCodeInClass(int statusCode, StatusCodeClass statusCodeClass) {
        return statusCode / 100 == statusCodeClass.family;
    }
}
