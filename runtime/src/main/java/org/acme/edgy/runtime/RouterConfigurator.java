package org.acme.edgy.runtime;

import java.util.List;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;

import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.interceptors.ObservingProxyInterceptor;
import org.acme.edgy.runtime.interceptors.QueryParamPropagationInterceptor;
import org.acme.edgy.runtime.interceptors.UriTemplateInterceptor;

import io.quarkus.arc.All;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

@Dependent
public class RouterConfigurator {

    private final RoutingConfiguration routingConfiguration;
    private final OriginHttpClientManager originHttpClientManager;
    private final List<ProxyObserver> observers;

    RouterConfigurator(RoutingConfiguration routingConfiguration,
            OriginHttpClientManager originHttpClientManager,
            @All List<ProxyObserver> observers) {
        this.routingConfiguration = routingConfiguration;
        this.originHttpClientManager = originHttpClientManager;
        this.observers = observers;
    }

    void configure(@Observes Router router) {
        for (Route route : routingConfiguration.routes()) {
            HttpClient httpClient = originHttpClientManager.getOrCreateHttpClient(route.origin());

            HttpProxy proxy = HttpProxy.reverseProxy(httpClient)
                    .origin(route.origin().originRequestProvider());
            addInterceptor(proxy, new ObservingProxyInterceptor(observers, route), !observers.isEmpty());
            addInterceptor(proxy, new UriTemplateInterceptor(route));
            addInterceptor(proxy, new QueryParamPropagationInterceptor());

            // order (response -> request) of the transformers is important!
            // this is because the transformers are implemented as only half of the
            // interceptors and to make sure that upon request transfomers failure (i.e.,
            // ErrorProxyResponseBuilder) the response transformers are still executed -
            // thats why the response transformers are added before the request
            // transformers)
            for (ResponseTransformer transformer : route.responseTransformers()) {
                proxy.addInterceptor(new ProxyInterceptor() {
                    @Override
                    public Future<Void> handleProxyResponse(ProxyContext context) {
                        return transformer.apply(context);
                    }
                });
            }
            for (RequestTransformer transformer : route.requestTransformers()) {
                proxy.addInterceptor(new ProxyInterceptor() {
                    @Override
                    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                        return transformer.apply(context);
                    }
                });
            }

            route.origin().guardInterceptor().ifPresent(proxy::addInterceptor);
            registerVertxRoute(router, route, proxy);
        }
    }

    private void addInterceptor(HttpProxy proxy, ProxyInterceptor interceptor) {
        addInterceptor(proxy, interceptor, true);
    }

    private void addInterceptor(HttpProxy proxy, ProxyInterceptor interceptor, boolean applicable) {
        if (applicable) {
            proxy.addInterceptor(interceptor);
        }
    }

    private void registerVertxRoute(Router router, Route edgyRoute, HttpProxy proxy) {
        var vertxRoute = edgyRoute.needsRegexRouting()
                ? router.routeWithRegex(edgyRoute.resolvedPath())
                : router.route(edgyRoute.resolvedPath());
        ProxyHandler proxyHandler = ProxyHandler.create(proxy);
        vertxRoute.handler(rc -> {
            if (edgyRoute.predicate().test(rc)) {
                proxyHandler.handle(rc);
                return;
            }
            // if the predicate does not match, it will sequentially try the next route
            // (with the same Path), if it exists
            rc.next();
        });
    }
}
