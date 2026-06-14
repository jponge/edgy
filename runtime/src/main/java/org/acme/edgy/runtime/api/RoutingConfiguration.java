package org.acme.edgy.runtime.api;

import java.util.ArrayList;
import java.util.List;

public class RoutingConfiguration {

    private final List<Route> routes;

    private RoutingConfiguration(List<Route> routes) {
        this.routes = List.copyOf(routes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Route> routes() {
        return routes;
    }

    public static class Builder {

        private final List<Route> routes = new ArrayList<>();

        private Builder() {
        }

        public Builder addRoute(Route route) {
            routes.add(route);
            return this;
        }

        public RoutingConfiguration build() {
            return new RoutingConfiguration(routes);
        }
    }
}
