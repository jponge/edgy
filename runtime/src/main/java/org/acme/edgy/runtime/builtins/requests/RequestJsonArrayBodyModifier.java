package org.acme.edgy.runtime.builtins.requests;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.acme.edgy.runtime.builtins.AbstractJsonArrayBodyModifier;
import org.acme.edgy.runtime.builtins.AbstractJsonObjectBodyModifier;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestJsonArrayBodyModifier extends AbstractJsonArrayBodyModifier
        implements RequestTransformer {

    public RequestJsonArrayBodyModifier(BiFunction<ProxyContext, JsonArray, JsonArray> mapper) {
        super(mapper);
    }

    public RequestJsonArrayBodyModifier(Function<JsonArray, JsonArray> jsonTransformer) {
        super(jsonTransformer);
    }

    public RequestJsonArrayBodyModifier(JsonArray body) {
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
