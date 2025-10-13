package org.acme.edgy.runtime.builtins.requests;

import java.util.Objects;
import java.util.function.Function;
import org.acme.edgy.runtime.api.RequestTransformer;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

public class RequestContentTypeModifier extends RequestHeaderModifier {
    public RequestContentTypeModifier(Function<ProxyContext, String> mapper) {
        super(CONTENT_TYPE, Objects.requireNonNull(mapper));
    }

    public RequestContentTypeModifier(String newValue) {
        this(proxyContext -> newValue);
    }
}
