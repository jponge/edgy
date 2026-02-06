package org.acme.edgy.runtime.api;

import java.util.function.Predicate;

import io.vertx.ext.web.RoutingContext;

@FunctionalInterface
public interface RoutingPredicate extends Predicate<RoutingContext> {

    // TODO: should we have async predicates?
}
