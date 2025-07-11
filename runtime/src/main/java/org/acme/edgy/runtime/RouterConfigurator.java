package org.acme.edgy.runtime;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
@DefaultBean
public class RouterConfigurator {

    @Inject
    Vertx vertx;

    void configure(@Observes Router router) {
        router.get("/yolo")
                .handler(rc -> rc.endAndForget("Yolo!"));
    }
}
