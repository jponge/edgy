package org.acme.edgy.test;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.acme.edgy.runtime.config.EdgyConfig;
import org.acme.edgy.runtime.config.SquaresConfig;

@ApplicationScoped
@Path("/")
public class TestEndpoint {

    @GET
    @Path("yolo")
    public String yolo() {
        return "Yolo!";
    }

    @Inject
    EdgyConfig config;

    @Inject
    SquaresConfig squaresConfig;

    @GET
    @Path("config")
    public JsonObject config() {
        JsonObject flexes = new JsonObject();
        config.flexes().forEach((key, flex) -> {
            JsonObject extra = new JsonObject();
            flex.extra().forEach(extra::put);
            flexes.put(key, new JsonObject()
                    .put("id", flex.id())
                    .put("extra", extra));
        });
        JsonObject payload = new JsonObject()
                .put("host", config.host())
                .put("port", config.port())
                .put("foo", new JsonObject().put("bar", config.foo().bar()))
                .put("flexes", flexes);
//        Log.info(payload.encodePrettily());
        for (EdgyConfig.Shape shape : config.shapes()) {
            Log.info("➡️ " + shape.name());
        }
        squaresConfig.squares().forEach((s, square) -> {
            Log.info(s + " = " + square.side());
        });
        return payload;

    }
}
