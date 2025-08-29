package org.acme.edgy.runtime.api;

import io.vertx.ext.web.RoutingContext;

import java.util.function.Predicate;

@FunctionalInterface
public interface RoutingPredicate extends Predicate<RoutingContext> {

    // TODO: should we have async predicates?
}
