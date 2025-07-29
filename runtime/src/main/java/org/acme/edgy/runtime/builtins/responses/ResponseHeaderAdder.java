package org.acme.edgy.runtime.builtins.responses;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import org.acme.edgy.runtime.api.ResponseTransformer;

import java.util.function.Function;

public class ResponseHeaderAdder implements ResponseTransformer {

    private final String name;
    private final Function<ProxyContext, String> mapper;

    public ResponseHeaderAdder(String name, Function<ProxyContext, String> mapper) {
        this.name = name;
        this.mapper = mapper;
    }

    public ResponseHeaderAdder(String name, String fixedValue) {
        this(name, proxyContext -> fixedValue);
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        proxyContext.response().putHeader(name, mapper.apply(proxyContext));
        return proxyContext.sendResponse();
    }
}
