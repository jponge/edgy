package org.acme.edgy.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.config.EdgyRoutes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DynamicRoutingConfigurationProvider {

    @Inject
    EdgyRoutes routes;

    @Inject
    Logger logger;

    @Produces
    public RoutingConfiguration getFromConfiguration() {
        // TODO
        logger.warn("Dynamic routing configuration is not implemented yet");
        return new RoutingConfiguration();
    }
}
