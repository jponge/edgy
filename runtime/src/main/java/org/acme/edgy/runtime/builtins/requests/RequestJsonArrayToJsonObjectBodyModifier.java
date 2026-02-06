package org.acme.edgy.runtime.builtins.requests;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;
import org.acme.edgy.runtime.builtins.AbstractJsonArrayToJsonObjectBodyModifier;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Request transformer that converts JSON Array bodies to JSON Object bodies. Useful for
 * transforming array responses into structured objects.
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
