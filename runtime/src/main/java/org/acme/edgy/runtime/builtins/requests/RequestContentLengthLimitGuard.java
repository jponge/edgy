package org.acme.edgy.runtime.builtins.requests;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;

import java.util.Objects;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

public class RequestContentLengthLimitGuard implements RequestTransformer {

    private static final String ERROR_MESSAGE_TEMPLATE = "Request content length %d exceeds the limit of %d";

    private final Function<ProxyContext, Long> mapper;

    public RequestContentLengthLimitGuard(Function<ProxyContext, Long> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public RequestContentLengthLimitGuard(long contentLength) {
        this(proxyContext -> contentLength);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        ProxyRequest request = proxyContext.request();
        Long contentLengthLimit = mapper.apply(proxyContext);
        long actualContentLength = Long.parseLong(request.headers().get(CONTENT_LENGTH));

        if (actualContentLength > contentLengthLimit) {
            return ProxyErrorResponseBuilder.create(proxyContext)
                    .payloadTooLarge()
                    .message(ERROR_MESSAGE_TEMPLATE.formatted(actualContentLength, contentLengthLimit))
                    .sendResponseInRequestTransformer();
        }
        return proxyContext.sendRequest();
    }
}
