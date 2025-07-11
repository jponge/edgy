package org.acme.edgy.runtime.api;

import java.util.ArrayList;
import java.util.List;

public class Route {

    private final String path;
    private final String origin;
    private final List<RoutingPredicate> predicates = new ArrayList<>();

    public Route(String path, String origin) {
        this.path = path;
        this.origin = origin;
    }

    public String path() {
        return path;
    }

    public String origin() {
        return origin;
    }

    public List<RoutingPredicate> predicates() {
        return predicates;
    }

    public Route addPredicate(RoutingPredicate predicate) {
        predicates.add(predicate);
        return this;
    }
}
