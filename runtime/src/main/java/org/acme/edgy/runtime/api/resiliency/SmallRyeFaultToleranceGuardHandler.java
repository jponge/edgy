package org.acme.edgy.runtime.api.resiliency;

import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import jakarta.enterprise.util.TypeLiteral;

import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

import io.smallrye.faulttolerance.api.RateLimitException;
import io.smallrye.faulttolerance.api.TypedGuard;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Wraps a SmallRye Fault Tolerance {@link TypedGuard} to implement the
 * {@link GuardHandler} contract. Instances are immutable; use
 * {@link #builder()} to configure and create one.
 *
 * @see GuardHandler
 */
public final class SmallRyeFaultToleranceGuardHandler implements GuardHandler {

    private final TypedGuard<Future<ProxyResponse>> guard;
    private final boolean needsBuffering;
    private final Expectation<ProxyResponse> expectation;
    private final BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback;
    private final long maxPayloadSize;

    private SmallRyeFaultToleranceGuardHandler(TypedGuard<Future<ProxyResponse>> guard,
            boolean needsBuffering,
            Expectation<ProxyResponse> expectation,
            BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback,
            long maxPayloadSize) {
        this.guard = guard;
        this.needsBuffering = needsBuffering;
        this.expectation = expectation;
        this.fallback = fallback;
        this.maxPayloadSize = maxPayloadSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Future<ProxyResponse> execute(Callable<Future<ProxyResponse>> action) throws Exception {
        return guard.call(action).recover(throwable -> {
            if (throwable instanceof RateLimitException rle) {
                return Future.failedFuture(
                        new RateLimitRejectedException(rle.getRetryAfterMillis()));
            } else if (throwable instanceof CircuitBreakerOpenException) {
                return Future.failedFuture(CircuitBreakerRejectedException.INSTANCE);
            }
            return Future.failedFuture(throwable);
        });
    }

    @Override
    public boolean needsBuffering() {
        return needsBuffering;
    }

    @Override
    public Expectation<ProxyResponse> expectation() {
        return expectation;
    }

    @Override
    public BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback() {
        return fallback;
    }

    @Override
    public long maxPayloadSize() {
        return maxPayloadSize;
    }

    public static final class Builder {

        private final TypedGuard.Builder<Future<ProxyResponse>> delegate;

        private Consumer<TypedGuard.Builder.RateLimitBuilder<Future<ProxyResponse>>> rateLimitConsumer;
        private Consumer<TypedGuard.Builder.BulkheadBuilder<Future<ProxyResponse>>> bulkheadConsumer;
        private Consumer<TypedGuard.Builder.CircuitBreakerBuilder<Future<ProxyResponse>>> circuitBreakerConsumer;
        private Consumer<TypedGuard.Builder.RetryBuilder<Future<ProxyResponse>>> retryConsumer;
        private Expectation<ProxyResponse> expectation;
        private BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback;
        private long maxPayloadSize = -1;

        Builder() {
            this.delegate = TypedGuard.create(new TypeLiteral<Future<ProxyResponse>>() {
            });
        }

        public Builder withRateLimit(
                Consumer<TypedGuard.Builder.RateLimitBuilder<Future<ProxyResponse>>> configurer) {
            this.rateLimitConsumer = configurer;
            return this;
        }

        public Builder withBulkhead(
                Consumer<TypedGuard.Builder.BulkheadBuilder<Future<ProxyResponse>>> configurer) {
            this.bulkheadConsumer = configurer;
            return this;
        }

        public Builder withCircuitBreaker(
                Consumer<TypedGuard.Builder.CircuitBreakerBuilder<Future<ProxyResponse>>> configurer) {
            this.circuitBreakerConsumer = configurer;
            return this;
        }

        public Builder withRetry(
                Consumer<TypedGuard.Builder.RetryBuilder<Future<ProxyResponse>>> configurer) {
            this.retryConsumer = configurer;
            return this;
        }

        public Builder withExpectation(Expectation<ProxyResponse> expectation) {
            this.expectation = expectation;
            return this;
        }

        public Builder withFallback(
                BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback) {
            this.fallback = fallback;
            return this;
        }

        public Builder withMaxPayloadSize(long maxPayloadSize) {
            this.maxPayloadSize = maxPayloadSize;
            return this;
        }

        public SmallRyeFaultToleranceGuardHandler build() {
            if (rateLimitConsumer != null) {
                var rl = delegate.withRateLimit();
                rateLimitConsumer.accept(rl);
                rl.done();
            }
            if (bulkheadConsumer != null) {
                var bh = delegate.withBulkhead();
                bulkheadConsumer.accept(bh);
                bh.done();
            }
            if (circuitBreakerConsumer != null) {
                var cb = delegate.withCircuitBreaker();
                circuitBreakerConsumer.accept(cb);
                cb.done();
            }
            if (retryConsumer != null) {
                var rt = delegate.withRetry();
                retryConsumer.accept(rt);
                rt.done();
            }
            Expectation<ProxyResponse> resolvedExpectation = expectation != null
                    ? expectation
                    : DEFAULT_EXPECTATION;
            return new SmallRyeFaultToleranceGuardHandler(delegate.build(),
                    retryConsumer != null, resolvedExpectation, fallback, maxPayloadSize);
        }
    }
}
