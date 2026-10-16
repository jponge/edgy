package org.acme.edgy.runtime.builtins.transformers.responses;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;
import org.acme.edgy.runtime.builtins.transformers.AbstractJsonObjectBodyModifier;
import org.acme.edgy.runtime.builtins.transformers.BodySizeLimitExceededException;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

/**
 * Transforms or replaces a JSON object response body. An empty body is
 * treated as an empty object. Returns 502 on malformed JSON from the
 * origin, 413 if the body exceeds the accumulator size limit.
 */
public class ResponseJsonObjectBodyModifier extends AbstractJsonObjectBodyModifier
        implements ResponseTransformer {

    public ResponseJsonObjectBodyModifier(BiFunction<ProxyContext, JsonObject, JsonObject> mapper) {
        super(mapper);
    }

    public ResponseJsonObjectBodyModifier(UnaryOperator<JsonObject> jsonTransformer) {
        super(jsonTransformer);
    }

    public ResponseJsonObjectBodyModifier(JsonObject body) {
        super(body);
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        if (mapper == null) {
            return applyStaticBody(proxyContext, ProxyContext::sendResponse);
        }

        return applyDynamicBody(proxyContext, ProxyContext::sendResponse).recover(throwable -> {
            if (throwable instanceof DecodeException) {
                return ProxyErrorResponseBuilder.create(proxyContext)
                        .badRequest()
                        .message(throwable.getMessage())
                        .sendResponseInResponseTransformer();
            }
            if (throwable instanceof BodySizeLimitExceededException) {
                return ProxyErrorResponseBuilder.create(proxyContext)
                        .payloadTooLarge()
                        .message(throwable.getMessage())
                        .sendResponseInResponseTransformer();
            }
            return Future.failedFuture(throwable);
        });

    }

    @Override
    protected Body getBody(ProxyContext proxyContext) {
        return proxyContext.response().getBody();
    }

    @Override
    protected void setBody(ProxyContext proxyContext, Body body) {
        proxyContext.response().setBody(body);
    }

}
