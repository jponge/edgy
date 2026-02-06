package org.acme.edgy.runtime.builtins.requests;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.acme.edgy.runtime.builtins.AbstractJsonObjectToJsonArrayBodyModifier;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Request transformer that converts JSON Object bodies to JSON Array bodies. Useful for
 * transforming structured objects into array format.
 */
public class RequestJsonObjectToJsonArrayBodyModifier
        extends AbstractJsonObjectToJsonArrayBodyModifier implements RequestTransformer {

    public RequestJsonObjectToJsonArrayBodyModifier(
            BiFunction<ProxyContext, JsonObject, JsonArray> mapper) {
        super(mapper);
    }

    public RequestJsonObjectToJsonArrayBodyModifier(
            Function<JsonObject, JsonArray> jsonTransformer) {
        super(jsonTransformer);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        return applyDynamicBody(proxyContext, ProxyContext::sendRequest).recover(throwable -> {
            if (throwable instanceof DecodeException) {
                return ProxyResponseFactory.badRequestInRequestTransformer(proxyContext,
                        throwable.getMessage());
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
