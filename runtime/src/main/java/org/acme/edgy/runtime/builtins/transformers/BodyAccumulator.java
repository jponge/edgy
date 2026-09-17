package org.acme.edgy.runtime.builtins.transformers;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.runtime.configuration.MemorySize;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.Body;

public final class BodyAccumulator {

    private static final String MAX_BODY_SIZE_KEY = "quarkus.http.limits.max-body-size";

    private BodyAccumulator() {
    }

    public static Future<Buffer> readBodyBuffer(Body body) {
        return readBodyBuffer(body, getMaxBodySize());
    }

    public static Future<Buffer> readBodyBuffer(Body body, long maxBodySize) {
        ReadStream<Buffer> stream = body.stream();
        Promise<Buffer> promise = Promise.promise();
        Buffer accumulator = Buffer.buffer();

        stream.handler(chunk -> {
            if (chunk != null) {
                if (accumulator.length() + chunk.length() > maxBodySize) {
                    promise.fail(new BodySizeLimitExceededException(
                            "Body size exceeded the limit of " + maxBodySize + " bytes"));
                    return;
                }
                accumulator.appendBuffer(chunk);
            }
        }).endHandler(v -> {
            if (!promise.future().isComplete()) {
                promise.complete(accumulator);
            }
        }).exceptionHandler(t -> {
            if (!promise.future().isComplete()) {
                promise.fail(t);
            }
        }).resume();

        return promise.future();
    }

    private static long getMaxBodySize() {
        return ConfigProvider.getConfig()
                .getValue(MAX_BODY_SIZE_KEY, MemorySize.class)
                .asLongValue();
    }
}
