package org.acme.edgy.runtime.builtins.responses;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.acme.edgy.runtime.builtins.AbstractJsonArrayBodyModifier;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

public class ResponseJsonArrayBodyModifier extends AbstractJsonArrayBodyModifier
        implements ResponseTransformer {

    public ResponseJsonArrayBodyModifier(BiFunction<ProxyContext, JsonArray, JsonArray> mapper) {
        super(mapper);
    }

    public ResponseJsonArrayBodyModifier(Function<JsonArray, JsonArray> jsonTransformer) {
        super(jsonTransformer);
    }

    public ResponseJsonArrayBodyModifier(JsonArray body) {
        super(body);
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        if (mapper == null) {
            return applyStaticBody(proxyContext, ProxyContext::sendResponse);
        }

        return applyDynamicBody(proxyContext, ProxyContext::sendResponse).recover(throwable -> {
            if (throwable instanceof DecodeException) {
                return ProxyResponseFactory.badRequestInResponseTransformer(proxyContext,
                        throwable.getMessage());
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
