package org.acme.edgy.runtime.interceptors.resiliency;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.RETRY_AFTER;

import java.util.Objects;

import org.acme.edgy.runtime.api.resiliency.CircuitBreakerRejectedException;
import org.acme.edgy.runtime.api.resiliency.GuardHandler;
import org.acme.edgy.runtime.api.resiliency.RateLimitRejectedException;
import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;
import org.acme.edgy.runtime.builtins.transformers.BodyAccumulator;
import org.acme.edgy.runtime.builtins.transformers.BodySizeLimitExceededException;

import io.vertx.core.Future;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Applies a {@link GuardHandler}'s resilience guard to proxied requests.
 *
 * <p>
 * When a fallback is configured, it receives the current {@link ProxyContext}
 * and the causing {@link Throwable} on every failure, allowing it to produce
 * a fresh response per request. Without a fallback, guard-specific exceptions
 * are mapped to HTTP status codes:
 * <ul>
 * <li>{@link RateLimitRejectedException} &rarr; 429 (Too Many Requests) with
 * {@code Retry-After} header</li>
 * <li>{@link CircuitBreakerRejectedException} &rarr; 503 (Service
 * Unavailable)</li>
 * </ul>
 *
 * <p>
 * Independently of the fallback, a
 * {@link BodySizeLimitExceededException} during body buffering is always
 * mapped to 413 (Payload Too Large).
 *
 * @see GuardHandler
 * @see org.acme.edgy.runtime.api.Route#setGuardHandler
 */
public class GuardInterceptor implements ProxyInterceptor {

    private final GuardHandler handler;

    public GuardInterceptor(GuardHandler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        Future<Void> preparation = handler.needsBuffering()
                ? bufferBody(context.request())
                : Future.succeededFuture();

        return preparation.compose(v -> {
            try {
                return handler.execute(() -> {
                    long maxSize = handler.maxPayloadSize();
                    if (maxSize > 0) {
                        String cl = context.request().headers().get(CONTENT_LENGTH);
                        if (cl != null && Long.parseLong(cl) > maxSize) {
                            return ProxyErrorResponseBuilder.create(context)
                                    .payloadTooLarge()
                                    .message("Body size exceeded the limit of " + maxSize + " bytes")
                                    .sendResponseInRequestTransformer()
                                    .expecting(handler.expectation());
                        }
                        Body body = context.request().getBody();
                        if (body != null) {
                            return BodyAccumulator.readBodyBuffer(body, maxSize).compose(buf -> {
                                context.request().setBody(Body.body(buf));
                                return context.sendRequest().expecting(handler.expectation());
                            });
                        }
                    }
                    return context.sendRequest().expecting(handler.expectation());
                }).recover(throwable -> {
                            var fb = handler.fallback();
                            if (fb != null) {
                                return fb.apply(context, throwable);
                            }
                            if (throwable instanceof RateLimitRejectedException rateLimitException) {
                                String retryAfterValue = String
                                        .valueOf((rateLimitException.getRetryAfterMillis() + 999) / 1000);
                                return ProxyErrorResponseBuilder.create(context)
                                        .tooManyRequests()
                                        .header(RETRY_AFTER, retryAfterValue)
                                        .sendResponseInRequestTransformer();
                            } else if (throwable instanceof CircuitBreakerRejectedException) {
                                return ProxyErrorResponseBuilder.create(context)
                                        .serviceUnavailable()
                                        .sendResponseInRequestTransformer();
                            }
                            return Future.failedFuture(throwable);
                        });
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        }).recover(throwable -> {
            if (throwable instanceof BodySizeLimitExceededException) {
                return ProxyErrorResponseBuilder.create(context)
                        .payloadTooLarge()
                        .message(throwable.getMessage())
                        .sendResponseInRequestTransformer();
            }
            return Future.failedFuture(throwable);
        });
    }

    private Future<Void> bufferBody(ProxyRequest proxyRequest) {
        Body body = proxyRequest.getBody();
        if (body == null) {
            return Future.succeededFuture();
        }
        return BodyAccumulator.readBodyBuffer(body).map(collected -> {
            proxyRequest.setBody(Body.body(collected));
            return null;
        });
    }
}
