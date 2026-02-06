package org.acme.edgy.runtime.api;

import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;

@FunctionalInterface
public interface ResponseTransformer extends Function<ProxyContext, Future<Void>> {

}
