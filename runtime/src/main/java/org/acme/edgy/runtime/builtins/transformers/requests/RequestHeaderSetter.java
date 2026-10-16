package org.acme.edgy.runtime.builtins.transformers.requests;

import java.util.Objects;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/** Sets a request header unconditionally, overwriting any existing value. */
public class RequestHeaderSetter implements RequestTransformer {

    private final String name;
    private final Function<ProxyContext, String> mapper;

    public RequestHeaderSetter(String name, Function<ProxyContext, String> mapper) {
        this.name = Objects.requireNonNull(name);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public RequestHeaderSetter(String name, String fixedValue) {
        this(name, proxyContext -> fixedValue);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        proxyContext.request().putHeader(name, mapper.apply(proxyContext));
        return proxyContext.sendRequest();
    }
}
