package org.acme.edgy.runtime.scatter;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.acme.edgy.runtime.api.LegResponse;
import org.acme.edgy.runtime.builtins.transformers.BodyAccumulator;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.httpproxy.OriginRequestProvider;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Custom {@link ProxyContext} for scatter legs, modeled after
 * {@code io.vertx.httpproxy.impl.ReverseProxy.Proxy}.
 * At the base of the response chain, the response body is captured
 * instead of being sent to the client.
 */
class ScatterLegContext implements ProxyContext {

    private final ProxyRequest request;
    private final HttpClient client;
    private final OriginRequestProvider originRequestProvider;
    private final String originIdentifier;
    private final ListIterator<ProxyInterceptor> filters;
    private final Map<String, Object> attachments = new HashMap<>();
    private ProxyResponse response;

    private Buffer capturedBody;
    private int capturedStatusCode = -1;
    private MultiMap capturedHeaders;

    ScatterLegContext(ProxyRequest request, HttpClient client,
            OriginRequestProvider originRequestProvider, String originIdentifier,
            List<ProxyInterceptor> interceptors) {
        this.request = request;
        this.client = client;
        this.originRequestProvider = originRequestProvider;
        this.originIdentifier = originIdentifier;
        this.filters = interceptors.listIterator();
    }

    @Override
    public void set(String name, Object value) {
        attachments.put(name, value);
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        Object o = attachments.get(name);
        return type.isInstance(o) ? type.cast(o) : null;
    }

    @Override
    public HttpClient client() {
        return client;
    }

    @Override
    public ProxyRequest request() {
        return request;
    }

    @Override
    public ProxyResponse response() {
        return response;
    }

    @Override
    public Future<ProxyResponse> sendRequest() {
        if (filters.hasNext()) {
            return filters.next().handleProxyRequest(this);
        }
        return originRequestProvider.create(this)
                .compose(request::send);
    }

    @Override
    public Future<Void> sendResponse() {
        if (filters.hasPrevious()) {
            return filters.previous().handleProxyResponse(this);
        }
        return captureResponse();
    }

    private Future<Void> captureResponse() {
        capturedStatusCode = response.getStatusCode();
        capturedHeaders = MultiMap.caseInsensitiveMultiMap().addAll(response.headers());

        if (response.getBody() == null) {
            capturedBody = Buffer.buffer();
            return Future.succeededFuture();
        }
        return BodyAccumulator.readBodyBuffer(response.getBody()).map(buffer -> {
            capturedBody = buffer;
            return null;
        });
    }

    Future<LegResponse> execute() {
        return sendRequest()
                .compose(proxyResponse -> {
                    this.response = proxyResponse;
                    return sendResponse();
                })
                .map(v -> new LegResponse(
                        originIdentifier,
                        capturedBody,
                        capturedStatusCode,
                        capturedHeaders != null ? capturedHeaders : MultiMap.caseInsensitiveMultiMap(),
                        null));
    }
}
