package org.acme.edgy.runtime.builtins.requests;

import static org.acme.edgy.runtime.api.utils.QueryParamUtils.urlEncode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import jakarta.ws.rs.core.UriBuilder;

import org.acme.edgy.runtime.api.RequestTransformer;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestQueryParameterRemover implements RequestTransformer {

    private final Function<ProxyContext, Collection<String>> mapper;

    public RequestQueryParameterRemover(Function<ProxyContext, Collection<String>> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public RequestQueryParameterRemover(String name, String... names) {
        this(proxyContext -> {
            Collection<String> allNames = new ArrayList<>();
            allNames.add(Objects.requireNonNull(name));
            if (names != null) {
                Arrays.stream(names).map(Objects::requireNonNull).forEach(allNames::add);
            }
            return allNames;
        });
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        // while uriBuilder encodes its inputs, it does not encodes '%' sign, if by any chance the
        // input is already encoded, so to properly encode '%' sign we need to encode the inputs
        // ourselves
        Collection<String> namesToBeRemoved = mapper.apply(proxyContext);
        UriBuilder uriBuilder = UriBuilder.fromUri(proxyContext.request().getURI());
        for (String name : namesToBeRemoved) {
            uriBuilder.replaceQueryParam(urlEncode(name));
        }
        String finalURI = uriBuilder.build().toString();
        proxyContext.request().setURI(finalURI);
        return proxyContext.sendRequest();
    }
}