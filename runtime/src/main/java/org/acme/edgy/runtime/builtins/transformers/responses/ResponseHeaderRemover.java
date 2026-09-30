package org.acme.edgy.runtime.builtins.transformers.responses;

import org.acme.edgy.runtime.api.ResponseTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;

/** Removes a response header by name. No-op if the header is absent. */
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
