package org.acme.edgy.runtime.builtins.transformers.requests;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;
import org.acme.edgy.runtime.builtins.transformers.AbstractJsonArrayToJsonObjectBodyModifier;
import org.acme.edgy.runtime.builtins.transformers.BodySizeLimitExceededException;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Request transformer that converts JSON Array bodies to JSON Object bodies. Useful for
 * transforming array requests into structured objects. Returns 400 on malformed JSON,
 * 413 if the body exceeds the accumulator size limit.
 */
public class RequestJsonArrayToJsonObjectBodyModifier
        extends AbstractJsonArrayToJsonObjectBodyModifier implements RequestTransformer {

    public RequestJsonArrayToJsonObjectBodyModifier(
            BiFunction<ProxyContext, JsonArray, JsonObject> mapper) {
        super(mapper);
    }

    public RequestJsonArrayToJsonObjectBodyModifier(
            Function<JsonArray, JsonObject> jsonTransformer) {
        super(jsonTransformer);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        return applyDynamicBody(proxyContext, ProxyContext::sendRequest).recover(throwable -> {
            if (throwable instanceof DecodeException) {
                return ProxyErrorResponseBuilder.create(proxyContext)
                        .badRequest()
                        .message(throwable.getMessage())
                        .sendResponseInRequestTransformer();
            }
            if (throwable instanceof BodySizeLimitExceededException) {
                return ProxyErrorResponseBuilder.create(proxyContext)
                        .payloadTooLarge()
                        .message(throwable.getMessage())
                        .sendResponseInRequestTransformer();
            }
            return Future.failedFuture(throwable);
        });
    }

    @Override
    protected Body getBody(ProxyContext proxyContext) {
        return proxyContext.request().getBody();
    }

    @Override
    protected void setBody(ProxyContext proxyContext, Body body) {
        proxyContext.request().setBody(body);
    }
}
