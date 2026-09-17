package org.acme.edgy.runtime.interceptors;

import org.acme.edgy.runtime.api.ProxyTarget;

import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class MethodBodyInterceptor implements ProxyInterceptor {

    private final ProxyTarget<?> target;

    public MethodBodyInterceptor(ProxyTarget<?> target) {
        this.target = target;
    }

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        HttpMethod resolvedMethod = target.methodOverride() != null
                ? target.methodOverride()
                : context.request().getMethod();
        context.request().setMethod(resolvedMethod);

        if (!target.shouldForwardBody(resolvedMethod)) {
            context.request().setBody(null);
        }

        return context.sendRequest();
    }
}
