package org.acme.edgy.runtime.api.utils;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.PAYLOAD_TOO_LARGE;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.REQUEST_TIMEOUT;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.SERVICE_UNAVAILABLE;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.TOO_MANY_REQUESTS;

import java.util.Objects;
import org.jboss.logging.Logger;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public interface ProxyResponseFactory {

    // Request Transformer Methods
    static Future<ProxyResponse> customResponseInRequestTransformer(ProxyContext context,
            int statusCode, String message) {
        checkStatusCode(statusCode);
        ProxyResponse response = Objects.requireNonNull(context).request().release().response();
        return Future.succeededFuture(buildResponse(response, statusCode, message));
    }

    static Future<ProxyResponse> badRequestInRequestTransformer(ProxyContext context,
            String message) {
        return customResponseInRequestTransformer(context, BAD_REQUEST, message);
    }

    static Future<ProxyResponse> timeoutInRequestTransformer(ProxyContext context,
            String message) {
        return customResponseInRequestTransformer(context, REQUEST_TIMEOUT, message);
    }

    static Future<ProxyResponse> payloadTooLargeInRequestTransformer(ProxyContext context,
            String message) {
        return customResponseInRequestTransformer(context, PAYLOAD_TOO_LARGE, message);
    }

    static Future<ProxyResponse> tooManyRequestsInRequestTransformer(ProxyContext context,
            String message) {
        return customResponseInRequestTransformer(context, TOO_MANY_REQUESTS, message);
    }

    static Future<ProxyResponse> serviceUnavailableInRequestTransformer(ProxyContext context,
            String message) {
        return customResponseInRequestTransformer(context, SERVICE_UNAVAILABLE, message);
    }

    // Response Transformer Methods
    static Future<Void> customResponseInResponseTransformer(ProxyContext context, int statusCode,
            String message) {
        checkStatusCode(statusCode);
        return buildResponse(Objects.requireNonNull(context).response().release(), statusCode,
                message).send();
    }

    static Future<Void> badRequestInResponseTransformer(ProxyContext context, String message) {
        return customResponseInResponseTransformer(context, BAD_REQUEST, message);
    }

    static Future<Void> payloadTooLargeInResponseTransformer(ProxyContext context, String message) {
        return customResponseInResponseTransformer(context, PAYLOAD_TOO_LARGE, message);
    }

    static Future<Void> tooManyRequestsInResponseTransformer(ProxyContext context, String message) {
        return customResponseInResponseTransformer(context, TOO_MANY_REQUESTS, message);
    }

    private static ProxyResponse buildResponse(ProxyResponse proxyResponse, int statusCode,
            String message) {
        return proxyResponse.setStatusCode(statusCode).putHeader(CONTENT_TYPE, TEXT_PLAIN)
                .setBody(Body.body(Buffer.buffer(message)));
    }

    private static void checkStatusCode(int statusCode) {
        if (statusCode < 400 || statusCode >= 600) {
            // due to TEXT_PLAIN content type
            Logger.getLogger(ProxyResponseFactory.class).warnf(
                    "Creating non-4xx/5xx response (%d) is discouraged, %s class should be only used for error responses",
                    statusCode, ProxyResponseFactory.class.getName());
        }
    }
}
