package org.acme.edgy.runtime.api;

import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

@FunctionalInterface
public interface RequestTransformer extends Function<ProxyContext, Future<ProxyResponse>> {

}
