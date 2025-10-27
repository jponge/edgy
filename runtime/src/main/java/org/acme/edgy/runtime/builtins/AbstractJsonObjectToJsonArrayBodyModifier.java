package org.acme.edgy.runtime.builtins;

import java.util.function.BiFunction;
import java.util.function.Function;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.ProxyContext;

public abstract class AbstractJsonObjectToJsonArrayBodyModifier
        extends AbstractJsonBodyModifier<JsonObject, JsonArray> {

    protected AbstractJsonObjectToJsonArrayBodyModifier(
            BiFunction<ProxyContext, JsonObject, JsonArray> mapper) {
        super(mapper);
    }

    protected AbstractJsonObjectToJsonArrayBodyModifier(
            Function<JsonObject, JsonArray> jsonTransformer) {
        super(jsonTransformer);
    }

    @Override
    protected JsonObject bufferToInputJson(Buffer buffer) {
        return buffer.length() > 0 ? buffer.toJsonObject() : new JsonObject();
    }

    @Override
    protected Buffer jsonToBuffer(JsonArray json) {
        return json.toBuffer();
    }
}
