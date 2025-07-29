package org.acme.edgy.runtime.api;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;

import java.util.function.Function;

@FunctionalInterface
public interface ResponseTransformer extends Function<ProxyContext, Future<Void>> {

}
