package org.acme.edgy.runtime.builtins.requests;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.QueryParamUtils;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;
import jakarta.ws.rs.core.UriBuilder;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.EMPTY_QUERY_VALUE;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.extractEncodedQueryNames;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.urlEncode;

public class RequestQueryParameterReplacer implements RequestTransformer {

    private final String name;
    private final Function<ProxyContext, Collection<?>> mapper;

    public RequestQueryParameterReplacer(String name,
            Function<ProxyContext, Collection<?>> mapper) {
        this.name = Objects.requireNonNull(name);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public RequestQueryParameterReplacer(String name, Collection<?> values) {
        this(name, proxyContext -> values);
    }

    public RequestQueryParameterReplacer(String name, Object... values) {
        this(name, proxyContext -> {
            Collection<Object> allValues = new ArrayList<>();
            if (values != null) {
                Arrays.stream(values).forEach(allValues::add);
            }
            return allValues;
        });
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        // while uriBuilder encodes its inputs, it does not encodes '%' sign, if by any chance the
        // input is already encoded, so to properly encode '%' sign we need to encode the inputs
        // ourselves
        String uri = proxyContext.request().getURI();
        String encodedName = urlEncode(name);
        if (!extractEncodedQueryNames(uri).contains(encodedName)) {
            // UriBuilder#replaceQueryParam adds the query param if the name does not exist
            return proxyContext.sendRequest();
        }
        UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        Collection<?> values = mapper.apply(proxyContext);
        if (values == null || values.isEmpty()) {
            uriBuilder.replaceQueryParam(encodedName, EMPTY_QUERY_VALUE);
        } else {
            uriBuilder.replaceQueryParam(encodedName,
                    values.stream().map(String::valueOf).map(QueryParamUtils::urlEncode).toArray());
        }
        String finalURI = uriBuilder.build().toString();
        proxyContext.request().setURI(finalURI);
        return proxyContext.sendRequest();
    }
}
