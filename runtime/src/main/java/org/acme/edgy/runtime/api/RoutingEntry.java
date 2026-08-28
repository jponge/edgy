package org.acme.edgy.runtime.api;

/**
 * A path-bound entry in the routing table. Both {@link Route} and
 * {@link ScatterRoute} are routing entries, differing in how they
 * dispatch to origins.
 */
public sealed interface RoutingEntry permits Route, ScatterRoute {

    String path();

    PathMode pathMode();

    String resolvedPath();

    boolean needsRegexRouting();

    RoutingPredicate predicate();
}
