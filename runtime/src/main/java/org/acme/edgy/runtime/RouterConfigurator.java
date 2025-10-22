package org.acme.edgy.runtime;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.QueryParamUtils;
import io.quarkus.arc.DefaultBean;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;

import static org.acme.edgy.runtime.api.utils.QueryParamUtils.appendUriQueries;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.hasQuery;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.extractSegmentValues;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.replaceSegmentsWithRegex;

@ApplicationScoped
@DefaultBean
public class RouterConfigurator {

    private static final String REQUEST_URI = "__REQUEST_URI__";
    private static final String REQUEST_URI_AFTER_PREFIX = "__REQUEST_URI_AFTER_PREFIX__";
    private static final String REGEXP_ZERO_OR_MORE = "*";
    private static final String CURLY_BRACE = "{";

    @Inject
    Vertx vertx;

    @Inject
    RoutingConfiguration routingConfiguration;

    void configure(@Observes Router router) {
        HttpClient httpClient = vertx.createHttpClient();

        // TODO this is a very early hacky start

        for (Route route : routingConfiguration.routes()) {
            Origin origin = route.origin();
            HttpProxy proxy = HttpProxy.reverseProxy(httpClient)
                    .origin(origin.originRequestProvider()); // dynamically receive the origin

            rerouteProxyRequestAndResolveUriTemplate(proxy, route);

            // to include query params from the original API Gateway URI
            propagateQueryParams(proxy);

            // request transformers
            applyRequestTransformers(route.requestTransformers(), proxy);

            // response transformers
            applyResponseTransformers(route.responseTransformers(), proxy);

            registerVertxRoute(router, route, proxy);
        }
    }

    private void propagateQueryParams(HttpProxy proxy) {
        proxy.addInterceptor(new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                ProxyRequest proxyRequest = context.request();
                String originUri = proxyRequest.getURI();
                String encodedQueryOfApiGatewayUri = context.request().proxiedRequest().query();
                if (encodedQueryOfApiGatewayUri == null && !hasQuery(originUri)) {
                    // no queries present
                    return context.sendRequest();
                }
                if (encodedQueryOfApiGatewayUri != null) {
                    // appends originalAPIGatewayURI query params into the originUri
                    originUri = appendUriQueries(originUri, encodedQueryOfApiGatewayUri);
                }
                proxyRequest.setURI(originUri);
                return context.sendRequest();
            }
        });
    }

    private boolean pathNeedsUriTemplateResolving(String path) {
        return path.contains(CURLY_BRACE);
    }

    private void rerouteProxyRequestAndResolveUriTemplate(HttpProxy proxy, Route route) {
        proxy.addInterceptor(new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                ProxyRequest proxyRequest = context.request();
                String originPath = route.origin().path();
                if (!pathNeedsUriTemplateResolving(originPath)) {
                    proxyRequest.setURI(originPath);
                    return context.sendRequest();
                }
                UriTemplate uriTemplate = UriTemplate.of(originPath);
                Variables variables = Variables.variables()
                        .set(REQUEST_URI, proxyRequest.getURI());
                if (route.pathMode() == PathMode.PARAMS) {
                    extractSegmentValues(route.path(), proxyRequest.getURI())
                            .forEach(variables::set);
                } else if (route.pathMode() == PathMode.PREFIX) {
                    int starPos = route.path().indexOf(REGEXP_ZERO_OR_MORE);
                    variables.set(REQUEST_URI_AFTER_PREFIX,
                            proxyRequest.getURI().substring(starPos));
                }
                proxyRequest.proxiedRequest().params().forEach(variables::set);
                proxyRequest.setURI(uriTemplate.expandToString(variables));
                return context.sendRequest();

            }
        });
    }

    private void applyRequestTransformers(List<RequestTransformer> requestTransformers, HttpProxy proxy) {
        for (RequestTransformer requestTransformer : requestTransformers) {
            proxy.addInterceptor(new ProxyInterceptor() {
                @Override
                public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                    return requestTransformer.apply(context);
                }
            });
        }
    }

    private void applyResponseTransformers(List<ResponseTransformer> responseTransformers,
            HttpProxy proxy) {
        for (ResponseTransformer responseTransformer : responseTransformers) {
            proxy.addInterceptor(new ProxyInterceptor() {
                @Override
                public Future<Void> handleProxyResponse(ProxyContext context) {
                    return responseTransformer.apply(context);
                }
            });
        }
    }

    private void registerVertxRoute(Router router, Route edgyRoute, HttpProxy proxy) {
        var vertxRoute = switch (edgyRoute.pathMode()) {
            case FIXED, PREFIX -> router.route(edgyRoute.path());
            case PARAMS -> router.routeWithRegex(replaceSegmentsWithRegex(edgyRoute.path()));
            case REGEXP -> router.routeWithRegex(edgyRoute.path());
        };
        vertxRoute.handler(rc -> {
            if (edgyRoute.predicates().stream().allMatch(predicate -> predicate.test(rc))) {
                ProxyHandler.create(proxy).handle(rc);
                return;
            }
            // if the predicates do not match, it will sequentially try the next route (with
            // the same Path), if it exists
            rc.next();
        });
    }
}
