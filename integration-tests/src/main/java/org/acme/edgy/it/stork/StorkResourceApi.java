package org.acme.edgy.it.stork;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/api/stork")
class StorkResourceApi {
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String host;
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    int port;

    @Inject
    Vertx vertx;

    static final int FIRST_SERVICE_PORT = 8082;
    static final int SECOND_SERVICE_PORT = 8083;
    
    @GET
    @Path("/services")
    public String startLoadBalancedServices() {
        vertx.createHttpServer()
            .requestHandler(req -> { 
                if (!req.path().equals("/test/hello")) {
                    req.response().setStatusCode(NOT_FOUND).endAndForget();
                    return;
                }
                req.response().endAndForget(String.valueOf(FIRST_SERVICE_PORT)); 
            }).listenAndAwait(FIRST_SERVICE_PORT);

            vertx.createHttpServer()
            .requestHandler(req -> { 
                if (!req.path().equals("/test/hello")) {
                    req.response().setStatusCode(NOT_FOUND).endAndForget();
                    return;
                }
                req.response().endAndForget(String.valueOf(SECOND_SERVICE_PORT));
            }).listenAndAwait(SECOND_SERVICE_PORT);

        return "services started";
    }

    @Path("/consul")
    @GET
    public String startConsul() {
        ConsulClient client = ConsulClient.create(vertx, new ConsulClientOptions().setHost(host).setPort(port));
        client.registerServiceAndAwait(
                new ServiceOptions().setPort(FIRST_SERVICE_PORT).setAddress("localhost").setName("my-service").setId("first"));
        client.registerServiceAndAwait(
                new ServiceOptions().setPort(SECOND_SERVICE_PORT).setAddress("localhost").setName("my-service").setId("second"));
        return "consul started";

    }
}
