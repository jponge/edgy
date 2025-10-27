package org.acme.edgy.runtime.builtins.responses;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.acme.edgy.runtime.builtins.AbstractJsonObjectToJsonArrayBodyModifier;
import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

/**
 * Response transformer that converts JSON Object bodies to JSON Array bodies. Useful for
 * transforming structured objects into array format.
 */
public class ResponseJsonObjectToJsonArrayBodyModifier
        extends AbstractJsonObjectToJsonArrayBodyModifier implements ResponseTransformer {

    public ResponseJsonObjectToJsonArrayBodyModifier(
            BiFunction<ProxyContext, JsonObject, JsonArray> mapper) {
        super(mapper);
    }

    public ResponseJsonObjectToJsonArrayBodyModifier(
            Function<JsonObject, JsonArray> jsonTransformer) {
        super(jsonTransformer);
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
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
