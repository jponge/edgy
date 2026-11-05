package org.acme.edgy.runtime.builtins.transformers.responses;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;
import org.acme.edgy.runtime.builtins.transformers.AbstractJsonArrayToJsonObjectBodyModifier;
import org.acme.edgy.runtime.builtins.transformers.BodySizeLimitExceededException;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

/**
 * Response transformer that converts JSON Array bodies to JSON Object bodies. Useful for
 * transforming array responses into structured objects. Returns 502 on malformed JSON
 * from the origin, 413 if the body exceeds the accumulator size limit.
 */
public class ResponseJsonArrayToJsonObjectBodyModifier
        extends AbstractJsonArrayToJsonObjectBodyModifier implements ResponseTransformer {

    public ResponseJsonArrayToJsonObjectBodyModifier(
            BiFunction<ProxyContext, JsonArray, JsonObject> mapper) {
        super(mapper);
    }

    public ResponseJsonArrayToJsonObjectBodyModifier(
            Function<JsonArray, JsonObject> jsonTransformer) {
        super(jsonTransformer);
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        return applyDynamicBody(proxyContext, ProxyContext::sendResponse).recover(throwable -> {
            if (throwable instanceof DecodeException) {
                return ProxyErrorResponseBuilder.create(proxyContext)
                        .badGateway()
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
