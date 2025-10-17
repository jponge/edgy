package org.acme.edgy.runtime.builtins.requests;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.acme.edgy.runtime.builtins.AbstractJSONBodyModifier;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestJSONBodyModifier extends AbstractJSONBodyModifier
        implements RequestTransformer {

    public RequestJSONBodyModifier(BiFunction<ProxyContext, JsonObject, JsonObject> mapper) {
        super(mapper);
    }

    public RequestJSONBodyModifier(Function<JsonObject, JsonObject> jsonTransformer) {
        super(jsonTransformer);
    }

    public RequestJSONBodyModifier(JsonObject body) {
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

    @Override
    protected BodyAccessor getBody(ProxyContext proxyContext) {
        return new BodyAccessor() {
            @Override
            public Body getBody() {
                return proxyContext.request().getBody();
            }

            @Override
            public void setBody(Body body) {
                proxyContext.request().setBody(body);
            }
        };
    }
}
