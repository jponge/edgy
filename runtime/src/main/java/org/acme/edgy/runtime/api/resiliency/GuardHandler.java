package org.acme.edgy.runtime.api.resiliency;

import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import org.acme.edgy.runtime.api.utils.StatusCode;

import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Guards proxied requests on a route by wrapping a {@link Callable} action
 * with fault tolerance patterns (rate limit, bulkhead, circuit breaker,
 * retry).
 *
 * @see SmallRyeFaultToleranceGuardHandler
 * @see org.acme.edgy.runtime.api.Route#setGuardHandler(GuardHandler)
 */
public interface GuardHandler {

    Expectation<ProxyResponse> DEFAULT_EXPECTATION = StatusCode.SC_NON_SERVER_ERROR;

    /**
     * Executes the given action wrapped with the configured resilience
     * patterns.
     *
     * @param action the proxy action to guard, never {@code null}
     * @return the guarded response future
     * @throws Exception if the guard rejects the action synchronously
     */
    Future<ProxyResponse> execute(Callable<Future<ProxyResponse>> action) throws Exception;

    /**
     * Indicates whether the request body must be buffered before execution
     * so that it can be re-sent on retries or replays.
     */
    boolean needsBuffering();

    default Expectation<ProxyResponse> expectation() {
        return DEFAULT_EXPECTATION;
    }

    default BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback() {
        return null;
    }

    default long maxPayloadSize() {
        return -1;
    }
}
