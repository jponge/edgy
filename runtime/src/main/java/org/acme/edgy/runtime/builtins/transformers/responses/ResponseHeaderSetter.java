package org.acme.edgy.runtime.builtins.transformers.responses;

import java.util.function.Function;

import org.acme.edgy.runtime.api.ResponseTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;

public class ResponseHeaderSetter implements ResponseTransformer {

    private final String name;
    private final Function<ProxyContext, String> mapper;

    public ResponseHeaderSetter(String name, Function<ProxyContext, String> mapper) {
        this.name = name;
        this.mapper = mapper;
    }

    public ResponseHeaderSetter(String name, String fixedValue) {
        this(name, proxyContext -> fixedValue);
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        proxyContext.response().putHeader(name, mapper.apply(proxyContext));
        return proxyContext.sendResponse();
    }
}
