package org.acme.edgy.runtime.interceptors.scatter;

import org.acme.edgy.runtime.api.Leg;

import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class MethodBodyInterceptor implements ProxyInterceptor {

    public static final String BUFFERED_BODY_KEY = "scatter.leg.bufferedBody";

    private final Leg leg;

    public MethodBodyInterceptor(Leg leg) {
        this.leg = leg;
    }

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        HttpMethod resolvedMethod = leg.methodOverride() != null
                ? leg.methodOverride()
                : context.request().getMethod();
        context.request().setMethod(resolvedMethod);

        if (leg.shouldForwardBody(resolvedMethod)) {
            Body bufferedBody = context.get(BUFFERED_BODY_KEY, Body.class);
            context.request().setBody(bufferedBody);
        } else {
            context.request().setBody(null);
        }

        return context.sendRequest();
    }
}
