package org.acme.edgy.runtime.builtins;

import java.util.function.BiFunction;
import java.util.function.Function;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.ProxyContext;

public abstract class AbstractJsonObjectBodyModifier
        extends AbstractJsonBodyModifier<JsonObject, JsonObject> {

    protected AbstractJsonObjectBodyModifier(
            BiFunction<ProxyContext, JsonObject, JsonObject> mapper) {
        super(mapper);
    }

    protected AbstractJsonObjectBodyModifier(Function<JsonObject, JsonObject> jsonTransformer) {
        super(jsonTransformer);
    }

    protected AbstractJsonObjectBodyModifier(JsonObject body) {
        super(body);
    }

    @Override
    protected JsonObject bufferToInputJson(Buffer buffer) {
        return buffer.length() > 0 ? buffer.toJsonObject() : new JsonObject();
    }

    @Override
    protected Buffer jsonToBuffer(JsonObject json) {
        return json.toBuffer();
    }
}
