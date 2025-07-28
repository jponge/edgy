package org.acme.edgy.runtime;

import io.quarkus.arc.DefaultBean;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;

@ApplicationScoped
@DefaultBean
public class RouterConfigurator {

    @Inject
    Vertx vertx;

    @Inject
    RoutingConfiguration routingConfiguration;
    private HttpClient httpClient;

    void configure(@Observes Router router) {
        httpClient = vertx.createHttpClient();

        // TODO this is a very early hacky start
        for (Route route : routingConfiguration.routes()) {
            OriginSpec originSpec = OriginSpec.of(route.origin(), route.pathMode());
            HttpProxy proxy = HttpProxy.reverseProxy(httpClient)
                    .origin(originSpec.port(), originSpec.host())
                    .addInterceptor(new ProxyInterceptor() {
                        @Override
                        public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                            context.request().setURI(originSpec.path());
                            return context.sendRequest();
                        }
                    });

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
