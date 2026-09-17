package org.acme.edgy.runtime.interceptors.scatter;

import org.acme.edgy.runtime.api.utils.HttpMethodUtils;

import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class ScatterMethodBodyInterceptor implements ProxyInterceptor {

    public static final String BUFFERED_BODY_KEY = "scatter.leg.bufferedBody";

    private final HttpMethod effectiveMethodOverride;
    private final boolean effectiveKeepBody;

    public ScatterMethodBodyInterceptor(HttpMethod effectiveMethodOverride, boolean effectiveKeepBody) {
        this.effectiveMethodOverride = effectiveMethodOverride;
        this.effectiveKeepBody = effectiveKeepBody;
    }

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        HttpMethod resolvedMethod = effectiveMethodOverride != null
                ? effectiveMethodOverride
                : context.request().getMethod();
        context.request().setMethod(resolvedMethod);

        boolean forwardBody = effectiveKeepBody
                || HttpMethodUtils.hasRequestBodySemantics(resolvedMethod);

        if (forwardBody) {
            Body bufferedBody = context.get(BUFFERED_BODY_KEY, Body.class);
            context.request().setBody(bufferedBody);
            return context.sendRequest();
        }
        context.request().setBody(null);
        return context.sendRequest();
    }
}
