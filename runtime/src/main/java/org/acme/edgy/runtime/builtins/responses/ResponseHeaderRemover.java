package org.acme.edgy.runtime.builtins.responses;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import org.acme.edgy.runtime.api.ResponseTransformer;

public class ResponseHeaderRemover implements ResponseTransformer {

    private final String name;

    public ResponseHeaderRemover(String name) {
        this.name = name;
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        proxyContext.response().headers().remove(name);
        return proxyContext.sendResponse();
    }
}
