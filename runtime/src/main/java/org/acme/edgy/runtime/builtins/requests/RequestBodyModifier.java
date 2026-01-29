package org.acme.edgy.runtime.builtins.requests;

import java.util.Objects;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestBodyModifier implements RequestTransformer {

    private final Function<ProxyContext, Body> mapper;

    public RequestBodyModifier(Function<ProxyContext, Body> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public RequestBodyModifier(Body body) {
        this(proxyContext -> body);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        proxyContext.request().setBody(mapper.apply(proxyContext));
        return proxyContext.sendRequest();
    }
}
