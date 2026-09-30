package org.acme.edgy.runtime.builtins.transformers.responses;

import java.util.Objects;
import java.util.function.Function;

import org.acme.edgy.runtime.api.ResponseTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;

/** Replaces a response header only if it already exists; no-op otherwise. */
public class ResponseHeaderReplacer implements ResponseTransformer {
    private final String name;
    private final Function<ProxyContext, String> mapper;

    public ResponseHeaderReplacer(String name, Function<ProxyContext, String> mapper) {
        this.name = Objects.requireNonNull(name);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public ResponseHeaderReplacer(String name, String newValue) {
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
