package org.acme.edgy.runtime.builtins.transformers.requests;

import java.util.Objects;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/** Replaces a request header only if it already exists; no-op otherwise. */
public class RequestHeaderReplacer implements RequestTransformer {
    private final String name;
    private final Function<ProxyContext, String> mapper;

    public RequestHeaderReplacer(String name, Function<ProxyContext, String> mapper) {
        this.name = Objects.requireNonNull(name);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public RequestHeaderReplacer(String name, String newValue) {
        this(name, proxyContext -> newValue);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        if (proxyContext.request().headers().contains(name)) {
            proxyContext.request().headers().set(name, mapper.apply(proxyContext));
        }
        return proxyContext.sendRequest();
    }
}
