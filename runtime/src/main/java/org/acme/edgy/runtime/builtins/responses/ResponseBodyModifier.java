package org.acme.edgy.runtime.builtins.responses;

import java.util.Objects;
import java.util.function.Function;
import org.acme.edgy.runtime.api.ResponseTransformer;
import io.vertx.core.Future;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

public class ResponseBodyModifier implements ResponseTransformer {

    // TODO support for JSON/XML content type body handling
    // 5.x Vertx HTTP Proxy seems to have a basic implementation for this use case
    // see io.vertx.httpproxy.BodyTransformer

    private final Function<ProxyContext, Body> mapper;

    public ResponseBodyModifier(Function<ProxyContext, Body> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public ResponseBodyModifier(Body body) {
        this(proxyContext -> body);
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        proxyContext.response().setBody(mapper.apply(proxyContext));
        return proxyContext.sendResponse();
    }
}
