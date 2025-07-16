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

import java.net.URI;
import java.net.URISyntaxException;

import static java.util.Objects.requireNonNullElse;

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
            OriginSpec originSpec = OriginSpec.of(route.origin());
            HttpProxy proxy = HttpProxy.reverseProxy(httpClient)
                    .origin(originSpec.port, originSpec.host)
                    .addInterceptor(new ProxyInterceptor() {
                        @Override
                        public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                            context.request().setURI(originSpec.path);
                            return context.sendRequest();
                        }
                    });
            router.route(route.path()).handler(ProxyHandler.create(proxy));
        }
    }

    record OriginSpec(String protocol, String host, int port, String path) {
        static OriginSpec of(String spec) {
            try {
                URI uri = new URI(spec);
                String uScheme = uri.getScheme();
                String uHost = uri.getHost();
                int uPort = uri.getPort();
                String uPath = uri.getPath();
                return new OriginSpec(
                        requireNonNullElse(uScheme, "http"),
                        requireNonNullElse(uHost, "localhost"),
                        uPort > 0 ? uPort : 8080,
                        requireNonNullElse(uPath, "/"));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
