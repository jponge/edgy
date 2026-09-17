package org.acme.edgy.runtime.api;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;

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
