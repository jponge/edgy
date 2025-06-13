package org.acme.edgy.it;

import io.quarkus.logging.Log;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ManualRoutes {

    void setup(@Observes Router router) {
        Log.info("Setting up manual routes");
        router.get("/foo").handler(ctx -> ctx.response().end("Foo"));
        router.get("/bar").handler(ctx -> ctx.response().end("Bar"));
    }
}
