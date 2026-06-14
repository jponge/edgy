package org.acme.edgy.runtime;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.config.EdgyRoutes;
import org.jboss.logging.Logger;

@Singleton
public class DynamicRoutingConfigurationProvider {

    private final EdgyRoutes routes;
    private final Logger logger;

    DynamicRoutingConfigurationProvider(EdgyRoutes routes, Logger logger) {
        this.routes = routes;
        this.logger = logger;
    }

    @Produces
    @Singleton
    public RoutingConfiguration getFromConfiguration() {
        // TODO
        logger.warn("Dynamic routing configuration is not implemented yet");
        return RoutingConfiguration.builder().build();
    }
}
