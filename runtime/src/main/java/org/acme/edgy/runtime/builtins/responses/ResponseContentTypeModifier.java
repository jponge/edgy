package org.acme.edgy.runtime.builtins.responses;

import java.util.Objects;
import java.util.function.Function;
import org.acme.edgy.runtime.api.ResponseTransformer;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

public class ResponseContentTypeModifier extends ResponseHeaderModifier {
    public ResponseContentTypeModifier(Function<ProxyContext, String> mapper) {
        super(CONTENT_TYPE, Objects.requireNonNull(mapper));
    }

    public ResponseContentTypeModifier(String newValue) {
        this(proxyContext -> newValue);
    }
}
