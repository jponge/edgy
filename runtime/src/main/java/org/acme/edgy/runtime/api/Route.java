package org.acme.edgy.runtime.api;

import java.util.ArrayList;
import java.util.List;

public class Route {

    private final String path;
    private final Origin origin;
    private final PathMode pathMode;
    private final List<RoutingPredicate> predicates = new ArrayList<>();
    private final List<RequestTransformer> requestTransformers = new ArrayList<>();

    public Route(String path, Origin origin, PathMode pathMode) {
        this.path = path;
        this.origin = origin;
        this.pathMode = pathMode;
    }

    public String path() {
        return path;
    }

    public Origin origin() {
        return origin;
    }

    public PathMode pathMode() {
        return pathMode;
    }

    public List<RoutingPredicate> predicates() {
        return predicates;
    }

    public Route addPredicate(RoutingPredicate predicate) {
        predicates.add(predicate);
        return this;
    }

    public List<RequestTransformer> requestTransformers() {
        return requestTransformers;
    }

    public Route addRequestTransformer(RequestTransformer requestTransformer) {
        requestTransformers.add(requestTransformer);
        return this;
    }
}
