package org.acme.edgy.runtime.api.utils;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.acme.edgy.runtime.api.utils.StatusCode.BAD_GATEWAY;
import static org.acme.edgy.runtime.api.utils.StatusCode.BAD_REQUEST;
import static org.acme.edgy.runtime.api.utils.StatusCode.INTERNAL_SERVER_ERROR;
import static org.acme.edgy.runtime.api.utils.StatusCode.PAYLOAD_TOO_LARGE;
import static org.acme.edgy.runtime.api.utils.StatusCode.SERVICE_UNAVAILABLE;
import static org.acme.edgy.runtime.api.utils.StatusCode.TOO_MANY_REQUESTS;
import static org.acme.edgy.runtime.api.utils.StatusCode.isError;

import java.util.Objects;

import org.jboss.logging.Logger;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public final class ProxyErrorResponseBuilder {

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

    public ProxyErrorResponseBuilder statusCode(int statusCode) {
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
        return statusCode(BAD_REQUEST);
    }

    public ProxyErrorResponseBuilder badGateway() {
        return statusCode(BAD_GATEWAY);
    }

    public ProxyErrorResponseBuilder payloadTooLarge() {
        return statusCode(PAYLOAD_TOO_LARGE);
    }

    public ProxyErrorResponseBuilder tooManyRequests() {
        return statusCode(TOO_MANY_REQUESTS);
    }

    public ProxyErrorResponseBuilder serviceUnavailable() {
        return statusCode(SERVICE_UNAVAILABLE);
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
        return context.sendResponse();
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
        if (!isError(statusCode)) {
            logger.warnf(
                    "Creating non-4xx/5xx response (%d) is discouraged, %s should only be used for error responses",
                    statusCode, this.getClass().getSimpleName());
        }
    }
}