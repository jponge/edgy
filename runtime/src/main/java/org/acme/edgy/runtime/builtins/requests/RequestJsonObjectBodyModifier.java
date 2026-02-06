package org.acme.edgy.runtime.builtins.requests;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.acme.edgy.runtime.builtins.AbstractJsonObjectBodyModifier;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestJsonObjectBodyModifier extends AbstractJsonObjectBodyModifier
        implements RequestTransformer {

    public RequestJsonObjectBodyModifier(BiFunction<ProxyContext, JsonObject, JsonObject> mapper) {
        super(mapper);
    }

    public RequestJsonObjectBodyModifier(Function<JsonObject, JsonObject> jsonTransformer) {
        super(jsonTransformer);
    }

    public RequestJsonObjectBodyModifier(JsonObject body) {
        super(body);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        if (mapper == null) {
            return applyStaticBody(proxyContext, ProxyContext::sendRequest);
        }

        return applyDynamicBody(proxyContext, ProxyContext::sendRequest).recover(throwable -> {
            if (throwable instanceof DecodeException) {
                return ProxyResponseFactory.badRequestInRequestTransformer(proxyContext,
                        throwable.getMessage());
            }
            return Future.failedFuture(throwable);
        });

    }

    // @Override
    // protected BodyAccessor getBody(ProxyContext proxyContext) {
    // return new BodyAccessor() {
    // @Override
    // public Body getBody() {
    // return proxyContext.request().getBody();
    // }

    // @Override
    // public void setBody(Body body) {
    // proxyContext.request().setBody(body);
    // }
    // };
    // }

    @Override
    protected Body getBody(ProxyContext proxyContext) {
        return proxyContext.request().getBody();
    }

    @Override
    protected void setBody(ProxyContext proxyContext, Body body) {
        proxyContext.request().setBody(body);
    }
}
