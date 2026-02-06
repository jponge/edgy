package org.acme.edgy.runtime.builtins.requests;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import jakarta.enterprise.util.TypeLiteral;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import io.smallrye.faulttolerance.api.RateLimitException;
import io.smallrye.faulttolerance.api.TypedGuard;
import io.smallrye.faulttolerance.api.TypedGuard.Builder;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestFaultToleranceApplier implements RequestTransformer {

    private final AtomicReference<TypedGuard<Future<ProxyResponse>>> guardRef = new AtomicReference<>();

    private final BiFunction<ProxyContext, Builder<Future<ProxyResponse>>, Builder<Future<ProxyResponse>>> mapper;
    private final Expectation<ProxyResponse> expectation;

    public RequestFaultToleranceApplier(
            BiFunction<ProxyContext, Builder<Future<ProxyResponse>>, Builder<Future<ProxyResponse>>> mapper,
            Expectation<ProxyResponse> expectation) {
        this.mapper = Objects.requireNonNull(mapper);
        this.expectation = Objects.requireNonNull(expectation);
    }

    public RequestFaultToleranceApplier(
            BiFunction<ProxyContext, Builder<Future<ProxyResponse>>, Builder<Future<ProxyResponse>>> mapper) {
        this(mapper, response -> response.getStatusCode() < 400);
    }

    public RequestFaultToleranceApplier(
            Function<Builder<Future<ProxyResponse>>, Builder<Future<ProxyResponse>>> guardBuilder) {
        this((proxyContext, builder) -> guardBuilder.apply(builder));
    }

    public RequestFaultToleranceApplier(
            Function<Builder<Future<ProxyResponse>>, Builder<Future<ProxyResponse>>> guardBuilder,
            Expectation<ProxyResponse> expectation) {
        this((proxyContext, builder) -> guardBuilder.apply(builder), expectation);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        try {
            return getGuard(proxyContext).call(() -> proxyContext.sendRequest().expecting(expectation))
                            .recover(throwable -> {
                        if (throwable instanceof TimeoutException) {
                            return ProxyResponseFactory.timeoutInRequestTransformer(proxyContext,
                                    throwable.getMessage());
                        } else if (throwable instanceof RateLimitException) {
                            return ProxyResponseFactory.tooManyRequestsInRequestTransformer(proxyContext,
                                    throwable.getMessage());
                        } else if (throwable instanceof CircuitBreakerOpenException) {
                            return ProxyResponseFactory.serviceUnavailableInRequestTransformer(proxyContext,
                                    throwable.getMessage());
                        }
                        // will transform to 502 Bad Gateway (includes expectation failures)
                        return Future.failedFuture(throwable);
                        // make sure to have proper exception class for `skipOn` method
                        // for example, if you have a guard with Circuit Breaker and Timeout configured,
                        // and 2xx, 3xx, and 429 (from the mapping above) make sure to exclude
                        // TimeoutException in the `skipOn` method, because by default the CB would
                        // count it as a failure, even tho it is expected
                    });
        } catch (Exception e) {
            // will tranform to 502 Bad Gateway
            return Future.failedFuture(e);
        }
    }

    private TypedGuard<Future<ProxyResponse>> getGuard(ProxyContext proxyContext) {
        // Lazily initialize the guard on the first request because the mapper requires
        // access to the runtime/request-only proxyContext.
        //
        // A singleton is required because stateful mechanisms (like rate limiting or
        // circuit breakers) would reset their counters if rebuilt on every request.
        //
        // We use AtomicReference.compareAndSet to remain lock-free. In a race on the
        // first request, multiple guards might be built, but only one will be
        // "set" and used for all future requests.
        TypedGuard<Future<ProxyResponse>> existing = guardRef.get();
        if (existing != null) {
            return existing;
        }

        TypedGuard<Future<ProxyResponse>> newGuard = mapper
                .apply(proxyContext, TypedGuard.create(new TypeLiteral<Future<ProxyResponse>>() {
                })).build();

        if (guardRef.compareAndSet(null, newGuard)) {
            return newGuard;
        }
        return guardRef.get();
    }
}
