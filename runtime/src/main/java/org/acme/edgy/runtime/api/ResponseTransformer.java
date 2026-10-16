package org.acme.edgy.runtime.api;

import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;

/**
 * Transforms a proxy response before it is sent back to the client.
 * Receives the full {@link ProxyContext} and must call
 * {@link ProxyContext#sendResponse()} exactly once to continue the chain.
 */
@FunctionalInterface
public interface ResponseTransformer extends Function<ProxyContext, Future<Void>> {

}
