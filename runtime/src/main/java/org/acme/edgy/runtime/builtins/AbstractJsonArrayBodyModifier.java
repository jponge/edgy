package org.acme.edgy.runtime.builtins;

import java.util.function.BiFunction;
import java.util.function.Function;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.httpproxy.ProxyContext;

public abstract class AbstractJsonArrayBodyModifier
        extends AbstractJsonBodyModifier<JsonArray, JsonArray> {

    protected AbstractJsonArrayBodyModifier(BiFunction<ProxyContext, JsonArray, JsonArray> mapper) {
        super(mapper);
    }

    protected AbstractJsonArrayBodyModifier(Function<JsonArray, JsonArray> jsonTransformer) {
        super(jsonTransformer);
    }

    protected AbstractJsonArrayBodyModifier(JsonArray body) {
        super(body);
    }

    @Override
    protected JsonArray bufferToInputJson(Buffer buffer) {
        return buffer.length() > 0 ? buffer.toJsonArray() : new JsonArray();
    }

    @Override
    protected Buffer jsonToBuffer(JsonArray json) {
        return json.toBuffer();
    }
}
