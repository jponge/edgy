package org.acme.edgy.runtime.api;

import static org.acme.edgy.runtime.api.utils.StatusCode.SC_NON_ERROR;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import org.acme.edgy.runtime.api.resiliency.GuardHandler;
import org.acme.edgy.runtime.interceptors.resiliency.GuardInterceptor;

import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class Leg {

    private final Origin origin;
    private HttpMethod methodOverride;
    private Boolean keepBody;
    private final List<RequestTransformer> requestTransformers = new ArrayList<>();
    private final Deque<ResponseTransformer> responseTransformers = new ArrayDeque<>();
    private ProxyInterceptor guardInterceptor;

    public Leg(Origin origin) {
        this.origin = Objects.requireNonNull(origin);
    }

    public Origin origin() {
        return origin;
    }

    public HttpMethod methodOverride() {
        return methodOverride;
    }

    public Leg setMethod(HttpMethod method) {
        this.methodOverride = Objects.requireNonNull(method);
        return this;
    }

    public Boolean keepBody() {
        return keepBody;
    }

    public Leg setKeepBody(boolean keep) {
        this.keepBody = keep;
        return this;
    }

    public boolean shouldForwardBody(HttpMethod resolvedMethod) {
        if (keepBody != null) {
            return keepBody;
        }
        return resolvedMethod == HttpMethod.POST
                || resolvedMethod == HttpMethod.PUT
                || resolvedMethod == HttpMethod.PATCH;
    }

    public List<RequestTransformer> requestTransformers() {
        return requestTransformers;
    }

    public Leg addRequestTransformer(RequestTransformer requestTransformer) {
        requestTransformers.add(requestTransformer);
        return this;
    }

    public Deque<ResponseTransformer> responseTransformers() {
        return responseTransformers;
    }

    public Leg addResponseTransformer(ResponseTransformer responseTransformer) {
        responseTransformers.addFirst(responseTransformer);
        return this;
    }

    public Leg setGuardHandler(GuardHandler handler) {
        return setGuardHandler(handler, SC_NON_ERROR);
    }

    public Leg setGuardHandler(GuardHandler handler, Expectation<ProxyResponse> expectation) {
        this.guardInterceptor = new GuardInterceptor(handler, expectation);
        return this;
    }

    public Leg setGuardHandler(GuardHandler handler,
            BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback) {
        return setGuardHandler(handler, SC_NON_ERROR, fallback);
    }

    public Leg setGuardHandler(GuardHandler handler, Expectation<ProxyResponse> expectation,
            BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback) {
        this.guardInterceptor = new GuardInterceptor(handler, expectation, fallback);
        return this;
    }

    public Optional<ProxyInterceptor> guardInterceptor() {
        return Optional.ofNullable(guardInterceptor);
    }
}
