package org.acme.edgy.it.stork;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
class StorkRoutingProvider {
    @Produces
    RoutingConfiguration basicRouting() {
        return new RoutingConfiguration().addRoute(new Route("/test", Origin.of("stork://my-service/test/hello"), PathMode.FIXED));
    }
}
