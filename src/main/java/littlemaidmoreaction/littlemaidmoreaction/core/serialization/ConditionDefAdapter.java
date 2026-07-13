package littlemaidmoreaction.littlemaidmoreaction.core.serialization;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * {@link ConditionDef} 的 Gson 适配器 — 兼容新旧两种 JSON 格式。
 *
 * <h3>支持的格式</h3>
 * <ul>
 *   <li>新格式 (v16 record): {@code {"key":"...", "op":"...", "val":"...", "params":{...}}}</li>
 *   <li>旧格式 (v12-v15): {@code {"key":"...", "operator":"...", "value":"...", "params":{...}}}</li>
 *   <li>布尔 (旧): {@code {"key":"..."}}</li>
 *   <li>布尔带参数 (新): {@code {"key":"...", "params":{...}}}</li>
 * </ul>
 */
public final class ConditionDefAdapter implements JsonSerializer<ConditionDef>, JsonDeserializer<ConditionDef> {

    private static final Type PARAMS_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    @Override
    public JsonElement serialize(ConditionDef src, Type typeOfSrc, JsonSerializationContext ctx) {
        JsonObject obj = new JsonObject();
        obj.addProperty("key", src.key());
        if (src.op() != null) obj.addProperty("op", src.op());
        if (src.val() != null) obj.addProperty("val", src.val());
        if (src.keyMath() != null) obj.addProperty("keyMath", src.keyMath());
        if (src.keyMathVal() != null) obj.addProperty("keyMathVal", src.keyMathVal());
        if (src.valMath() != null) obj.addProperty("valMath", src.valMath());
        if (src.valMathVal() != null) obj.addProperty("valMathVal", src.valMathVal());
        if (!src.params().isEmpty()) obj.add("params", ctx.serialize(src.params()));
        return obj;
    }

    @Override
    public ConditionDef deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx)
            throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        String key = getStringOrNull(obj, "key");
        if (key == null) throw new JsonParseException("ConditionDef missing 'key'");

        // 兼容新旧字段名: op/operator, val/value
        String op = getStringOrNull(obj, "op");
        if (op == null) op = getStringOrNull(obj, "operator");

        String val = getStringOrNull(obj, "val");
        if (val == null) val = getStringOrNull(obj, "value");

        String keyMath = getStringOrNull(obj, "keyMath");
        String keyMathVal = getStringOrNull(obj, "keyMathVal");
        String valMath = getStringOrNull(obj, "valMath");
        String valMathVal = getStringOrNull(obj, "valMathVal");

        Map<String, String> params = Map.of();
        if (obj.has("params")) {
            params = ctx.deserialize(obj.get("params"), PARAMS_TYPE);
        }

        return new ConditionDef(key, op, val, keyMath, keyMathVal, valMath, valMathVal, params);
    }

    private static String getStringOrNull(JsonObject obj, String name) {
        JsonElement elem = obj.get(name);
        return (elem != null && !elem.isJsonNull()) ? elem.getAsString() : null;
    }
}
