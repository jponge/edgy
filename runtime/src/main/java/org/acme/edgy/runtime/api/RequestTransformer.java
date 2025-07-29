package org.acme.edgy.runtime.api;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

import java.util.function.Function;

@FunctionalInterface
public interface RequestTransformer extends Function<ProxyContext, Future<ProxyResponse>> {

}
