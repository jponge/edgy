package org.acme.edgy.runtime.config;

import java.util.List;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

/**
 * Runtime-define routes.
 */
@ConfigMapping(prefix = "edgy.routes")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface EdgyRoutes {

    /**
     * Routes list.
     */
    @WithParentName
    List<Route> routes();

    /**
     * Defines a route.
     */
    interface Route {

        /**
         * The route identifier.
         */
        String id();

        /**
         * The route path.
         */
        String path();
    }
}
