package org.acme.edgy.runtime.api;

import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Transforms an outgoing proxy request before it reaches the origin.
 * Receives the full {@link ProxyContext} and returns the response future
 * produced by calling {@link ProxyContext#sendRequest()}.
 * <p>
 * Must call {@code context.sendRequest()} exactly once to continue the
 * chain. Returning a completed response without calling it short-circuits
 * the remaining interceptors and the origin call.
 */
@FunctionalInterface
public interface RequestTransformer extends Function<ProxyContext, Future<ProxyResponse>> {

}
