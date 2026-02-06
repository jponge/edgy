package org.acme.edgy.runtime.builtins.requests;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

import jakarta.ws.rs.core.MediaType;

import org.acme.edgy.runtime.api.RequestTransformer;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestContentTypeModifier implements RequestTransformer {
    private static final String FALLBACK_CHARSET = StandardCharsets.UTF_8.toString();

    private Function<ProxyContext, MediaType> mediaType;

    public RequestContentTypeModifier(Function<ProxyContext, MediaType> mapper) {
        this.mediaType = Objects.requireNonNull(mapper);
    }

    public RequestContentTypeModifier(MediaType mediaType) {
        this(proxyContext -> mediaType);
    }

    public RequestContentTypeModifier(String mediaType) {
        this(proxyContext -> MediaType.valueOf(mediaType));
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        String prevContentType = proxyContext.request().headers().get(CONTENT_TYPE);

        if (prevContentType == null) {
            MediaType newMediaType = mediaType.apply(proxyContext);
            proxyContext.request().headers().set(CONTENT_TYPE, newMediaType.toString());
            return proxyContext.sendRequest();
        }

        MediaType prevMediaType = MediaType.valueOf(prevContentType);
        MediaType newMediaType = mediaType.apply(proxyContext);

        if (prevMediaType.equals(newMediaType)) {
            return proxyContext.sendRequest();
        }

        String prevCharsetName = prevMediaType.getParameters().getOrDefault(MediaType.CHARSET_PARAMETER,
                FALLBACK_CHARSET);
        Charset prevCharset = Charset.forName(prevCharsetName);

        String newCharsetName = newMediaType.getParameters().getOrDefault(MediaType.CHARSET_PARAMETER,
                FALLBACK_CHARSET);
        Charset newCharset = Charset.forName(newCharsetName);

        proxyContext.request().headers().set(CONTENT_TYPE, newMediaType.toString());

        if (!prevCharset.equals(newCharset)) {
            Body body = proxyContext.request().getBody();
            if (body != null) {
                return readBodyBuffer(body).compose(bodyBuffer -> {
                    String content = bodyBuffer.toString(prevCharset);
                    Buffer reEncodedBuffer = Buffer.buffer(content.getBytes(newCharset));

                    proxyContext.request().headers().set(CONTENT_LENGTH, String.valueOf(reEncodedBuffer.length()));
                    proxyContext.request().setBody(Body.body(reEncodedBuffer));

                    return proxyContext.sendRequest();
                });
            }
        }

        return proxyContext.sendRequest();
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
