package org.acme.edgy.runtime.api;

import java.util.ArrayList;
import java.util.List;

public class RoutingConfiguration {

    private final List<Route> routes;
    private final List<ScatterRoute> scatterRoutes;

    private RoutingConfiguration(List<Route> routes, List<ScatterRoute> scatterRoutes) {
        this.routes = List.copyOf(routes);
        this.scatterRoutes = List.copyOf(scatterRoutes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Route> routes() {
        return routes;
    }

    public List<ScatterRoute> scatterRoutes() {
        return scatterRoutes;
    }

    public static class Builder {

        private final List<Route> routes = new ArrayList<>();
        private final List<ScatterRoute> scatterRoutes = new ArrayList<>();

        private Builder() {
        }

        public Builder addRoute(Route route) {
            routes.add(route);
            return this;
        }

        public Builder addScatterRoute(ScatterRoute scatterRoute) {
            scatterRoutes.add(scatterRoute);
            return this;
        }

        public RoutingConfiguration build() {
            for (ScatterRoute scatterRoute : scatterRoutes) {
                scatterRoute.validate();
            }
            return new RoutingConfiguration(routes, scatterRoutes);
        }
    }
}
