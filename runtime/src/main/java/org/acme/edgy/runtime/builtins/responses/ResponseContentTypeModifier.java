package org.acme.edgy.runtime.builtins.responses;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

import jakarta.ws.rs.core.MediaType;

import org.acme.edgy.runtime.api.ResponseTransformer;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

public class ResponseContentTypeModifier implements ResponseTransformer {
    private static final String FALLBACK_CHARSET = StandardCharsets.UTF_8.toString();

    private Function<ProxyContext, MediaType> mediaType;

    public ResponseContentTypeModifier(Function<ProxyContext, MediaType> mapper) {
        this.mediaType = Objects.requireNonNull(mapper);
    }

    public ResponseContentTypeModifier(MediaType mediaType) {
        this(proxyContext -> mediaType);
    }

    public ResponseContentTypeModifier(String mediaType) {
        this(proxyContext -> MediaType.valueOf(mediaType));
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        String prevContentType = proxyContext.response().headers().get(CONTENT_TYPE);

        if (prevContentType == null) {
            MediaType newMediaType = mediaType.apply(proxyContext);
            proxyContext.response().headers().set(CONTENT_TYPE, newMediaType.toString());
            return proxyContext.sendResponse();
        }

        MediaType prevMediaType = MediaType.valueOf(prevContentType);
        MediaType newMediaType = mediaType.apply(proxyContext);

        if (prevMediaType.equals(newMediaType)) {
            return proxyContext.sendResponse();
        }

        String prevCharsetName = prevMediaType.getParameters().getOrDefault(MediaType.CHARSET_PARAMETER,
                FALLBACK_CHARSET);
        Charset prevCharset = Charset.forName(prevCharsetName);

        String newCharsetName = newMediaType.getParameters().getOrDefault(MediaType.CHARSET_PARAMETER,
                FALLBACK_CHARSET);
        Charset newCharset = Charset.forName(newCharsetName);

        proxyContext.response().headers().set(CONTENT_TYPE, newMediaType.toString());

        if (!prevCharset.equals(newCharset)) {
            Body body = proxyContext.response().getBody();
            if (body != null) {
                return readBodyBuffer(body).compose(bodyBuffer -> {
                    String content = bodyBuffer.toString(prevCharset);
                    Buffer reEncodedBuffer = Buffer.buffer(content.getBytes(newCharset));

                    proxyContext.response().headers().set(CONTENT_LENGTH, String.valueOf(reEncodedBuffer.length()));
                    proxyContext.response().setBody(Body.body(reEncodedBuffer));

                    return proxyContext.sendResponse();
                });
            }
        }

        return proxyContext.sendResponse();
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
}