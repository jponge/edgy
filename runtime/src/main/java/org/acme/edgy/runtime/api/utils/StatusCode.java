package org.acme.edgy.runtime.api.utils;

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

    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int PAYLOAD_TOO_LARGE = 413;
    public static final int TOO_MANY_REQUESTS = 429;

    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int BAD_GATEWAY = 502;
    public static final int SERVICE_UNAVAILABLE = 503;

    public static final Expectation<ProxyResponse> SC_SUCCESS = response -> isSuccess(response.getStatusCode());
    public static final Expectation<ProxyResponse> SC_NON_ERROR = response -> !isError(response.getStatusCode());
    public static final Expectation<ProxyResponse> SC_NON_SERVER_ERROR = response -> !isServerError(response.getStatusCode());

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

    private static boolean isStatusCodeInClass(int statusCode, StatusCodeClass statusCodeClass) {
        return statusCode / 100 == statusCodeClass.family;
    }
}
