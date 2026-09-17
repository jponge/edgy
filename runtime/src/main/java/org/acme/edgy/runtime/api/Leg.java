package org.acme.edgy.runtime.api;

import org.acme.edgy.runtime.api.utils.HttpMethodUtils;

/**
 * A proxy target within a {@link ScatterRoute}. A leg has no path of
 * its own — it inherits the path context from its parent scatter route.
 * <p>
 * Scatter-level defaults (method, keepBody, transformers, guard) apply
 * unless this leg explicitly overrides them via its own setters.
 */
public class Leg extends ProxyTarget<Leg> {

    public Leg(Origin origin) {
        super(origin);
    }

    public boolean requiresBody() {
        if (keepBody()) {
            return true;
        }
        if (methodOverride() != null) {
            return HttpMethodUtils.hasRequestBodySemantics(methodOverride());
        }
        return true;
    }
}
