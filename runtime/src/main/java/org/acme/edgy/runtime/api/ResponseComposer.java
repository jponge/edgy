package org.acme.edgy.runtime.api;

import java.util.List;
import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

@FunctionalInterface
public interface ResponseComposer extends Function<List<LegResponse>, Future<Buffer>> {

}
