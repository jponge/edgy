package org.acme.edgy.runtime.builtins;

import java.util.function.BiFunction;
import java.util.function.Function;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.ProxyContext;

public abstract class AbstractJsonArrayToJsonObjectBodyModifier
        extends AbstractJsonBodyModifier<JsonArray, JsonObject> {

    protected AbstractJsonArrayToJsonObjectBodyModifier(
            BiFunction<ProxyContext, JsonArray, JsonObject> mapper) {
        super(mapper);
    }

    protected AbstractJsonArrayToJsonObjectBodyModifier(
            Function<JsonArray, JsonObject> jsonTransformer) {
        super(jsonTransformer);
    }

    @Override
    protected JsonArray bufferToInputJson(Buffer buffer) {
        return buffer.length() > 0 ? buffer.toJsonArray() : new JsonArray();
    }

    @Override
    protected Buffer jsonToBuffer(JsonObject json) {
        return json.toBuffer();
    }
}
