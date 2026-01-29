package org.acme.edgy.runtime.builtins.responses;

import java.util.Objects;
import java.util.function.Function;

import org.acme.edgy.runtime.api.ResponseTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;

public class ResponseHeaderModifier implements ResponseTransformer {
    private final String name;
    private final Function<ProxyContext, String> mapper;

    public ResponseHeaderModifier(String name, Function<ProxyContext, String> mapper) {
        this.name = Objects.requireNonNull(name);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public ResponseHeaderModifier(String name, String newValue) {
        this(name, proxyContext -> newValue);
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        if (proxyContext.response().headers().contains(name)) {
            proxyContext.response().headers().set(name, mapper.apply(proxyContext));
        }
        return proxyContext.sendResponse();
    }
}
