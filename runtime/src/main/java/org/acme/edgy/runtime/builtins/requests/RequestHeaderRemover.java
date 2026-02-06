package org.acme.edgy.runtime.builtins.requests;

import org.acme.edgy.runtime.api.RequestTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestHeaderRemover implements RequestTransformer {

    private final String name;

    public RequestHeaderRemover(String name) {
        this.name = name;
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        proxyContext.request().headers().remove(name);
        return proxyContext.sendRequest();
    }
}
