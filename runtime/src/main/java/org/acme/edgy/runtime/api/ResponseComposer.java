package org.acme.edgy.runtime.api;

import java.util.List;
import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * Combines the responses from a {@link ScatterRoute}'s legs into a single
 * response body. The list order matches the order legs were declared.
 * <p>
 * In {@link FailureMode#PARTIAL}, some entries may represent failed legs —
 * check {@link LegResponse#succeeded()} before reading body or headers.
 */
@FunctionalInterface
public interface ResponseComposer extends Function<List<LegResponse>, Future<Buffer>> {

}
