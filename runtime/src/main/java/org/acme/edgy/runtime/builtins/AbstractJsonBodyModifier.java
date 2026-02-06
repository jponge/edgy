package org.acme.edgy.runtime.builtins;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

/**
 * Base abstraction for all JSON body modifiers that transform JSON content.
 * 
 * @param <I> Input JSON type
 * @param <O> Output JSON type
 */
public abstract class AbstractJsonBodyModifier<I, O> {

    protected final BiFunction<ProxyContext, I, O> mapper;
    private final O fullNewJson; // for optimization when replacing entire body

    protected AbstractJsonBodyModifier(BiFunction<ProxyContext, I, O> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.fullNewJson = null;
    }

    protected AbstractJsonBodyModifier(Function<I, O> jsonTransformer) {
        this((proxyContext, body) -> jsonTransformer.apply(body));
    }

    protected AbstractJsonBodyModifier(O body) {
        this.mapper = null;
        this.fullNewJson = body;
    }

    protected <T> Future<T> applyStaticBody(ProxyContext proxyContext,
            Function<ProxyContext, Future<T>> sender) {
        Buffer buffer = Buffer.buffer();
        if (fullNewJson != null) { // allowing null for clearing the body
            buffer.appendBuffer(jsonToBuffer(fullNewJson));
        }
        setBody(proxyContext, Body.body(buffer));
        return sender.apply(proxyContext);
    }

    protected <T> Future<T> applyDynamicBody(ProxyContext proxyContext,
            Function<ProxyContext, Future<T>> sender) {
        Body body = getBody(proxyContext);

        if (body == null) {
            // No body to transform, just send
            return sender.apply(proxyContext);
        }

        return readBodyBuffer(body).compose(bodyBuffer -> {
            I oldJson = bufferToInputJson(bodyBuffer);
            O newJson = mapper.apply(proxyContext, oldJson);

            Buffer replacingBuffer = Buffer.buffer();
            if (newJson != null) { // allowing null for clearing the body
                replacingBuffer.appendBuffer(jsonToBuffer(newJson));
            }

            setBody(proxyContext, Body.body(replacingBuffer));

            return sender.apply(proxyContext);
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

    protected abstract I bufferToInputJson(Buffer buffer) throws DecodeException;

    protected abstract Buffer jsonToBuffer(O json);

    protected abstract Body getBody(ProxyContext proxyContext);

    protected abstract void setBody(ProxyContext proxyContext, Body body);
}
