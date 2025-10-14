package org.acme.edgy.runtime.builtins;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public abstract class AbstractJSONBodyModifier {

    protected final BiFunction<ProxyContext, JsonObject, JsonObject> mapper;
    private final JsonObject fullNewJson; // for optimization

    protected AbstractJSONBodyModifier(BiFunction<ProxyContext, JsonObject, JsonObject> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.fullNewJson = null;
    }

    protected AbstractJSONBodyModifier(Function<JsonObject, JsonObject> jsonTransformer) {
        this((proxyContext, body) -> jsonTransformer.apply(body));
    }

    protected AbstractJSONBodyModifier(JsonObject body) {
        this.mapper = null;
        this.fullNewJson = body;
    }

    protected <T> Future<T> applyStaticBody(ProxyContext proxyContext,
            Function<ProxyContext, Future<T>> sender) {
        modifyContentTypeToJsonIfNeeded(proxyContext);
        Buffer buffer = Buffer.buffer();
        if (fullNewJson != null) { // allowing null for clearing the body
            buffer.appendBuffer(fullNewJson.toBuffer());
        }
        getBody(proxyContext).setBody(Body.body(buffer));
        return sender.apply(proxyContext);
    }

    protected <T> Future<T> applyDynamicBody(ProxyContext proxyContext,
            Function<ProxyContext, Future<T>> sender) {
        Body body = getBody(proxyContext).getBody();

        if (body == null) {
            // No body to transform, just send
            return sender.apply(proxyContext);
        }

        return readBodyBuffer(body).compose(bodyBuffer -> {
            try {
                JsonObject oldJson =
                        bodyBuffer.length() > 0 ? bodyBuffer.toJsonObject() : new JsonObject();
                JsonObject newJson = mapper.apply(proxyContext, oldJson);

                Buffer replacingBuffer = Buffer.buffer();
                if (newJson != null) { // allowing null for clearing the body
                    replacingBuffer.appendBuffer(newJson.toBuffer());
                }

                modifyContentTypeToJsonIfNeeded(proxyContext);
                getBody(proxyContext).setBody(Body.body(replacingBuffer));

                return sender.apply(proxyContext);
            } catch (Exception e) {
                return Future.failedFuture(e); // 502
            }
        });
    }

    private Future<Buffer> readBodyBuffer(Body body) {
        Promise<Buffer> promise = Promise.promise();
        Buffer accumulator = Buffer.buffer();

        body.stream().handler(chunk -> {
            if (chunk != null) {
                accumulator.appendBuffer(chunk);
            }
        }).endHandler(v -> promise.complete(accumulator)).exceptionHandler(promise::fail).resume();

        return promise.future();
    }

    private void modifyContentTypeToJsonIfNeeded(ProxyContext proxyContext) {
        String contentType = getBody(proxyContext).headers().get(CONTENT_TYPE);
        if (!APPLICATION_JSON.equals(contentType)) {
            getBody(proxyContext).headers().set(CONTENT_TYPE, APPLICATION_JSON);
        }
    }

    // Returns the appropriate body accessor (request or response) for the concrete implementation
    protected abstract BodyAccessor getBody(ProxyContext proxyContext);

    // Interface to abstract access to request/response body and headers
    protected interface BodyAccessor {
        Body getBody();

        void setBody(Body body);

        MultiMap headers();
    }
}
