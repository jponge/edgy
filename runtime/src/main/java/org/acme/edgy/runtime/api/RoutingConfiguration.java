package org.acme.edgy.runtime.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable collection of routing entries built via {@link Builder}.
 * Produce a CDI bean of this type to register routes at startup.
 */
public class RoutingConfiguration {

    private final List<RoutingEntry> entries;

    private RoutingConfiguration(List<RoutingEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<RoutingEntry> entries() {
        return entries;
    }

    public List<Route> routes() {
        return entries.stream()
                .filter(Route.class::isInstance)
                .map(Route.class::cast)
                .toList();
    }

    public static class Builder {

        private final List<RoutingEntry> entries = new ArrayList<>();

        private Builder() {
        }

        public Builder addRoute(Route route) {
            entries.add(route);
            return this;
        }

        public Builder addScatterRoute(ScatterRoute scatterRoute) {
            entries.add(scatterRoute);
            return this;
        }

        public RoutingConfiguration build() {
            return new RoutingConfiguration(entries);
        }
    }
}
