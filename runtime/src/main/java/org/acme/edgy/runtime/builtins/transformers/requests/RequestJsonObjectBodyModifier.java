package org.acme.edgy.runtime.builtins.transformers.requests;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;
import org.acme.edgy.runtime.builtins.transformers.AbstractJsonObjectBodyModifier;
import org.acme.edgy.runtime.builtins.transformers.BodySizeLimitExceededException;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Transforms or replaces a JSON object request body. An empty body is
 * treated as an empty object. Returns 400 on malformed JSON, 413 if
 * the body exceeds the accumulator size limit.
 */
public class RequestJsonObjectBodyModifier extends AbstractJsonObjectBodyModifier
        implements RequestTransformer {

    public RequestJsonObjectBodyModifier(BiFunction<ProxyContext, JsonObject, JsonObject> mapper) {
        super(mapper);
    }

    public RequestJsonObjectBodyModifier(UnaryOperator<JsonObject> jsonTransformer) {
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
