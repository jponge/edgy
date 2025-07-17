package org.acme.edgy.runtime.api;

import io.vertx.ext.web.RoutingContext;

import java.util.function.Predicate;

public interface RoutingPredicate extends Predicate<RoutingContext> {

    @Override
    default boolean test(RoutingContext routingContext) {
        return true;
    }
}
