package org.acme.edgy.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.ProxyTarget;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.RoutingEntry;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.HttpMethodUtils;
import org.acme.edgy.runtime.interceptors.MethodBodyInterceptor;
import org.acme.edgy.runtime.interceptors.ObservingProxyInterceptor;
import org.acme.edgy.runtime.interceptors.QueryParamPropagationInterceptor;
import org.acme.edgy.runtime.interceptors.UriTemplateInterceptor;
import org.acme.edgy.runtime.interceptors.scatter.ScatterMethodBodyInterceptor;
import org.acme.edgy.runtime.scatter.ScatterHandler;

import io.quarkus.arc.All;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

@Dependent
public class RouterConfigurator {

    private final RoutingConfiguration routingConfiguration;
    private final OriginManager originManager;
    private final List<ProxyObserver> observers;

    RouterConfigurator(RoutingConfiguration routingConfiguration,
            OriginManager originManager,
            @All List<ProxyObserver> observers) {
        this.routingConfiguration = routingConfiguration;
        this.originManager = originManager;
        this.observers = observers;
    }

    void configure(@Observes Router router) {
        for (RoutingEntry entry : routingConfiguration.entries()) {
            if (entry instanceof Route route) {
                configureRoute(router, route);
            } else if (entry instanceof ScatterRoute scatter) {
                configureScatterRoute(router, scatter);
            }
        }
    }

    private void configureRoute(Router router, Route route) {
        HttpClient httpClient = originManager.getOrCreateHttpClient(route.origin());
        originManager.initializeCacheInterceptor(route.origin());

        HttpProxy proxy = HttpProxy.reverseProxy(httpClient)
                .origin(route.origin().originRequestProvider());

        addInterceptor(proxy::addInterceptor, new ObservingProxyInterceptor(observers, route), !observers.isEmpty());
        addInterceptor(proxy::addInterceptor, new MethodBodyInterceptor(route), route.methodOverride() != null);
        route.origin().cacheInterceptor().ifPresent(proxy::addInterceptor);
        addInterceptor(proxy::addInterceptor, new UriTemplateInterceptor(route));
        addInterceptor(proxy::addInterceptor, new QueryParamPropagationInterceptor());

        addTransformerInterceptors(proxy::addInterceptor, route);

        registerVertxRoute(router, route, proxy);
    }

    private void configureScatterRoute(Router router, ScatterRoute scatterRoute) {
        List<ScatterHandler.LegDefinition> legDefinitions = new ArrayList<>();

        for (Leg leg : scatterRoute.legs()) {
            HttpClient httpClient = originManager.getOrCreateHttpClient(leg.origin());
            originManager.initializeCacheInterceptor(leg.origin());
            Route syntheticRoute = new Route(scatterRoute.path(), leg.origin(), scatterRoute.pathMode());

            // precedence: leg-level overrides scatter-level
            HttpMethod effectiveMethod = leg.methodOverride() != null
                    ? leg.methodOverride() : scatterRoute.methodOverride();

            // precedence: leg-level overrides scatter-level, by default keepBody is false
            boolean effectiveKeepBody = leg.keepBodyOverride() != null
                    ? leg.keepBody() : scatterRoute.keepBody();

            // i.e. needs buffering
            boolean requiresBody = effectiveKeepBody || effectiveMethod == null
                    || HttpMethodUtils.hasRequestBodySemantics(effectiveMethod);

            // precedence: leg-level overrides scatter-level
            Optional<ProxyInterceptor> effectiveGuard = leg.guardInterceptor().isPresent()
                    ? leg.guardInterceptor() : scatterRoute.guardInterceptor();

            List<ProxyInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new ScatterMethodBodyInterceptor(effectiveMethod, effectiveKeepBody));
            leg.origin().cacheInterceptor().ifPresent(interceptors::add);
            addInterceptor(interceptors::add, new UriTemplateInterceptor(syntheticRoute));
            addInterceptor(interceptors::add, new QueryParamPropagationInterceptor());

            addScatterTransformerInterceptors(interceptors::add, scatterRoute, leg, effectiveGuard);

            legDefinitions.add(new ScatterHandler.LegDefinition(leg, httpClient, interceptors, requiresBody));
        }

        ScatterHandler handler = new ScatterHandler(scatterRoute, legDefinitions, observers);
        var vertxRoute = scatterRoute.needsRegexRouting()
                ? router.routeWithRegex(scatterRoute.resolvedPath())
                : router.route(scatterRoute.resolvedPath());
        vertxRoute.handler(handler);
    }

    // order (response → request) matters: response transformers are added first so
    // that when a request transformer fails (e.g. ErrorProxyResponseBuilder), the
    // already-registered response transformers still execute on the way back out.
    private static void addTransformerInterceptors(Consumer<ProxyInterceptor> adder, ProxyTarget<?> target) {
        for (ResponseTransformer transformer : target.responseTransformers()) {
            adder.accept(new ProxyInterceptor() {
                @Override
                public Future<Void> handleProxyResponse(ProxyContext context) {
                    return transformer.apply(context);
                }
            });
        }
        for (RequestTransformer transformer : target.requestTransformers()) {
            adder.accept(new ProxyInterceptor() {
                @Override
                public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                    return transformer.apply(context);
                }
            });
        }
        target.guardInterceptor().ifPresent(adder);
    }

    // Scatter transformer merge: scatter-level transformers execute before leg-level.
    // Response transformers: leg first (outermost), then scatter (executes first on response path).
    // Request transformers: scatter first (executes first on request path), then leg.
    private static void addScatterTransformerInterceptors(
            Consumer<ProxyInterceptor> adder,
            ScatterRoute scatter, ProxyTarget<?> leg,
            Optional<ProxyInterceptor> effectiveGuard) {

        for (ResponseTransformer t : leg.responseTransformers()) {
            adder.accept(new ProxyInterceptor() {
                @Override
                public Future<Void> handleProxyResponse(ProxyContext ctx) {
                    return t.apply(ctx);
                }
            });
        }
        for (ResponseTransformer t : scatter.responseTransformers()) {
            adder.accept(new ProxyInterceptor() {
                @Override
                public Future<Void> handleProxyResponse(ProxyContext ctx) {
                    return t.apply(ctx);
                }
            });
        }
        for (RequestTransformer t : scatter.requestTransformers()) {
            adder.accept(new ProxyInterceptor() {
                @Override
                public Future<ProxyResponse> handleProxyRequest(ProxyContext ctx) {
                    return t.apply(ctx);
                }
            });
        }
        for (RequestTransformer t : leg.requestTransformers()) {
            adder.accept(new ProxyInterceptor() {
                @Override
                public Future<ProxyResponse> handleProxyRequest(ProxyContext ctx) {
                    return t.apply(ctx);
                }
            });
        }
        effectiveGuard.ifPresent(adder);
    }

    private static void addInterceptor(Consumer<ProxyInterceptor> adder, ProxyInterceptor interceptor) {
        adder.accept(interceptor);
    }

    private static void addInterceptor(Consumer<ProxyInterceptor> adder, ProxyInterceptor interceptor,
            boolean applicable) {
        if (applicable) {
            adder.accept(interceptor);
        }
    }

    private static void registerVertxRoute(Router router, Route edgyRoute, HttpProxy proxy) {
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
