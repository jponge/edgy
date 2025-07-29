package org.acme.edgy.runtime;

import io.quarkus.arc.DefaultBean;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;

@ApplicationScoped
@DefaultBean
public class RouterConfigurator {

    @Inject
    Vertx vertx;

    @Inject
    RoutingConfiguration routingConfiguration;

    void configure(@Observes Router router) {
        HttpClient httpClient = vertx.createHttpClient();

        // TODO this is a very early hacky start

        for (Route route : routingConfiguration.routes()) {
            Origin origin = route.origin();
            UriTemplate uriTemplate = UriTemplate.of(origin.path());
            HttpProxy proxy = HttpProxy.reverseProxy(httpClient)
                    .origin(origin.originRequestProvider())
                    .addInterceptor(new ProxyInterceptor() {
                        @Override
                        public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                            Variables variables = Variables.variables();
                            variables.set("__REQUEST_URI__", context.request().getURI());
                            if (route.pathMode() == PathMode.PREFIX) {
                                int starPos = route.path().indexOf("*");
                                variables.set("__REQUEST_URI_AFTER_PREFIX__", context.request().getURI().substring(starPos));
                            }
                            MultiMap params = context.request().proxiedRequest().params();
                            params.forEach(variables::set);
                            context.request().setURI(uriTemplate.expandToString(variables));
                            return context.sendRequest();
                        }
                    });

            for (RequestTransformer requestTransformer : route.requestTransformers()) {
                proxy.addInterceptor(new ProxyInterceptor() {
                    @Override
                    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                        return requestTransformer.apply(context);
                    }
                });
            }

            io.vertx.ext.web.Route base = switch (route.pathMode()) {
                case FIXED -> router.route(route.path());
                case PREFIX -> router.route(route.path());
                case PARAMS -> router.route(route.path());
                case REGEXP -> router.routeWithRegex(route.path());
            };
            base.handler(rc -> {
                if (route.predicates().stream().allMatch(predicate -> predicate.test(rc))) {
                    ProxyHandler.create(proxy).handle(rc);
                } else {
                    rc.next();
                }
            });
        }
    }

}
