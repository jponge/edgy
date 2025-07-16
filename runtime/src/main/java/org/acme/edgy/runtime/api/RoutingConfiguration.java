package org.acme.edgy.runtime.api;

import java.util.ArrayList;
import java.util.List;

public class RoutingConfiguration {

    private final List<Route> routes =  new ArrayList<>();

    public RoutingConfiguration addRoute(Route route) {
        routes.add(route);
        return this;
    }

    public List<Route> routes() {
        return routes;
    }
}
