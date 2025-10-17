package org.acme.edgy.runtime.builtins.responses;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.acme.edgy.runtime.builtins.AbstractJSONBodyModifier;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

public class ResponseJSONBodyModifier extends AbstractJSONBodyModifier
        implements ResponseTransformer {

    public ResponseJSONBodyModifier(BiFunction<ProxyContext, JsonObject, JsonObject> mapper) {
        super(mapper);
    }

    public ResponseJSONBodyModifier(Function<JsonObject, JsonObject> jsonTransformer) {
        super(jsonTransformer);
    }

    public ResponseJSONBodyModifier(JsonObject body) {
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
    protected BodyAccessor getBody(ProxyContext proxyContext) {
        return new BodyAccessor() {
            @Override
            public Body getBody() {
                return proxyContext.response().getBody();
            }

            @Override
            public void setBody(Body body) {
                proxyContext.response().setBody(body);
            }
        };
    }
}
