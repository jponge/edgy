package org.acme.edgy.runtime.builtins.requests;

import java.util.Objects;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;

import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestHttpMethodModifier implements RequestTransformer {

    private final Function<ProxyContext, HttpMethod> mapper;

    public RequestHttpMethodModifier(Function<ProxyContext, HttpMethod> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public RequestHttpMethodModifier(HttpMethod method) {
        this(proxyContext -> Objects.requireNonNull(method));
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        proxyContext.request().setMethod(mapper.apply(proxyContext));
        return proxyContext.sendRequest();
    }
}
