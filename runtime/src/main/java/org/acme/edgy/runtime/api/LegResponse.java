package org.acme.edgy.runtime.api;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;

/**
 * The result of a single {@link Leg} in a scatter/gather exchange.
 * On failure, {@code statusCode} is {@code -1} and {@link #failure()}
 * is non-null — always check {@link #succeeded()} before reading
 * body or headers.
 */
public record LegResponse(
        String originIdentifier,
        Buffer body,
        int statusCode,
        MultiMap headers,
        Throwable failure) {

    public boolean succeeded() {
        return failure == null;
    }
}
