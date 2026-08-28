package org.acme.edgy.runtime.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.acme.edgy.runtime.api.resiliency.GuardHandler;
import org.acme.edgy.runtime.api.utils.HttpMethodUtils;
import org.acme.edgy.runtime.interceptors.resiliency.GuardInterceptor;

import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyInterceptor;

/**
 * A target that proxies requests to an origin with method mapping,
 * body handling, transformers, and resilience guards.
 * <p>
 * Both {@link Route} (a path-bound proxy target) and {@link Leg}
 * (a proxy target within a {@link ScatterRoute}) extend this class.
 * Fluent setters return the concrete subtype for chaining.
 * <p>
 * When used inside a {@link ScatterRoute}, scatter-level defaults
 * apply unless this target explicitly overrides them. A value is
 * considered explicitly set once the corresponding setter has been
 * called (e.g. {@link #setMethod}, {@link #setKeepBody}).
 */
public abstract class ProxyTarget<T extends ProxyTarget<T>> {

    private final Origin origin;
    private HttpMethod methodOverride;
    private Boolean keepBody;
    private final List<RequestTransformer> requestTransformers = new ArrayList<>();
    private final Deque<ResponseTransformer> responseTransformers = new ArrayDeque<>();
    private ProxyInterceptor guardInterceptor;

    protected ProxyTarget(Origin origin) {
        this.origin = Objects.requireNonNull(origin);
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }

    public Origin origin() {
        return origin;
    }

    public HttpMethod methodOverride() {
        return methodOverride;
    }

    public boolean keepBody() {
        return keepBody != null && keepBody;
    }

    /**
     * Returns {@code null} if keep-body was never explicitly set.
     */
    public Boolean keepBodyOverride() {
        return keepBody;
    }

    public boolean shouldForwardBody(HttpMethod resolvedMethod) {
        return keepBody() || HttpMethodUtils.hasRequestBodySemantics(resolvedMethod);
    }

    public List<RequestTransformer> requestTransformers() {
        return requestTransformers;
    }

    public Deque<ResponseTransformer> responseTransformers() {
        return responseTransformers;
    }

    public Optional<ProxyInterceptor> guardInterceptor() {
        return Optional.ofNullable(guardInterceptor);
    }

    /**
     * Overrides the HTTP method sent to the origin.
     */
    public T setMethod(HttpMethod method) {
        this.methodOverride = Objects.requireNonNull(method);
        return self();
    }

    public T setKeepBody(boolean keep) {
        this.keepBody = keep;
        return self();
    }

    /**
     * Adds a request transformer to the end of this target's chain.
     * When inside a scatter route, scatter-level request transformers
     * execute before this target's own transformers.
     */
    public T addRequestTransformer(RequestTransformer transformer) {
        requestTransformers.add(transformer);
        return self();
    }

    /**
     * Adds a response transformer to the front of this target's chain.
     * When inside a scatter route, scatter-level response transformers
     * execute before this target's own transformers.
     */
    public T addResponseTransformer(ResponseTransformer transformer) {
        responseTransformers.addFirst(transformer);
        return self();
    }

    /**
     * Sets a resilience guard for this proxy target.
     *
     * @param handler the guard handler carrying expectation, fallback, and payload limit
     * @see GuardHandler
     */
    public T setGuardHandler(GuardHandler handler) {
        this.guardInterceptor = new GuardInterceptor(handler);
        return self();
    }
}
