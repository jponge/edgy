package org.acme.edgy.runtime.builtins.transformers.responses;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

import jakarta.ws.rs.core.MediaType;

import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;
import org.acme.edgy.runtime.builtins.transformers.BodyAccumulator;
import org.acme.edgy.runtime.builtins.transformers.BodySizeLimitExceededException;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;

/**
 * Sets the response Content-Type header. When the charset changes, the
 * body is re-encoded automatically. Returns 413 if the body exceeds
 * the accumulator size limit during re-encoding.
 */
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
                return BodyAccumulator.readBodyBuffer(body).compose(bodyBuffer -> {
                    String content = bodyBuffer.toString(prevCharset);
                    Buffer reEncodedBuffer = Buffer.buffer(content.getBytes(newCharset));

                    proxyContext.response().setBody(Body.body(reEncodedBuffer));

                    return proxyContext.sendResponse();
                }).recover(throwable -> {
                    if (throwable instanceof BodySizeLimitExceededException) {
                        return ProxyErrorResponseBuilder.create(proxyContext)
                                .payloadTooLarge()
                                .message(throwable.getMessage())
                                .sendResponseInResponseTransformer();
                    }
                    return Future.failedFuture(throwable);
                });
            }
        }

        return proxyContext.sendResponse();
    }
}