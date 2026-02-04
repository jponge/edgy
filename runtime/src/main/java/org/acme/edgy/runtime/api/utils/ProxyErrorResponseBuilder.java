package org.acme.edgy.runtime.api.utils;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.INTERNAL_SERVER_ERROR;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.PAYLOAD_TOO_LARGE;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.REQUEST_TIMEOUT;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.SERVICE_UNAVAILABLE;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.TOO_MANY_REQUESTS;

import java.util.Objects;

import org.jboss.logging.Logger;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class ProxyErrorResponseBuilder {

    private static final Logger logger = Logger.getLogger(ProxyErrorResponseBuilder.class);

    private final ProxyContext context;
    private int statusCode = INTERNAL_SERVER_ERROR; // default
    private String message;
    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    private ProxyErrorResponseBuilder(ProxyContext context) {
        this.context = Objects.requireNonNull(context, "ProxyContext must not be null");
    }

    public static ProxyErrorResponseBuilder create(ProxyContext context) {
        return new ProxyErrorResponseBuilder(context);
    }

    public ProxyErrorResponseBuilder status(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public ProxyErrorResponseBuilder message(String message) {
        this.message = message;
        return this;
    }

    public ProxyErrorResponseBuilder header(CharSequence name, CharSequence value) {
        this.headers.add(name, value);
        return this;
    }

    public ProxyErrorResponseBuilder badRequest() {
        return status(BAD_REQUEST);
    }

    public ProxyErrorResponseBuilder timeout() {
        return status(REQUEST_TIMEOUT);
    }

    public ProxyErrorResponseBuilder payloadTooLarge() {
        return status(PAYLOAD_TOO_LARGE);
    }

    public ProxyErrorResponseBuilder tooManyRequests() {
        return status(TOO_MANY_REQUESTS);
    }

    public ProxyErrorResponseBuilder serviceUnavailable() {
        return status(SERVICE_UNAVAILABLE);
    }

    // --------------- build methods ---------------

    public Future<ProxyResponse> sendResponseInRequestTransformer() {
        validateStatusCode();
        ProxyResponse response = context.request().release().response();
        applyConfiguration(response);
        return Future.succeededFuture(response);
    }

    public Future<Void> sendResponseInResponseTransformer() {
        validateStatusCode();
        ProxyResponse response = context.response().release();
        applyConfiguration(response);
        return response.send();
    }
    // ---------------------------------------------

    private void applyConfiguration(ProxyResponse response) {
        response.setStatusCode(statusCode);

        response.putHeader(CONTENT_TYPE, TEXT_PLAIN);

        headers.forEach(response::putHeader);

        if (message != null) {
            response.setBody(Body.body(Buffer.buffer(message)));
        }
    }

    private void validateStatusCode() {
        if (statusCode < 400 || statusCode >= 600) {
            logger.warnf(
                    "Creating non-4xx/5xx response (%d) is discouraged, %s should only be used for error responses",
                    statusCode, this.getClass().getSimpleName());
        }
    }
}