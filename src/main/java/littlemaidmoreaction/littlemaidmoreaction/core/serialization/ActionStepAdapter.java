package littlemaidmoreaction.littlemaidmoreaction.core.serialization;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * {@link ActionStep} 的 Gson TypeAdapter。
 *
 * <p>从旧 data/ActionStep.Adapter 迁移至 core/serialization/ (v10)。</p>
 */
public final class ActionStepAdapter implements JsonSerializer<ActionStep>, JsonDeserializer<ActionStep> {

    private static final Type PARAMS_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    @Override
    public JsonElement serialize(ActionStep src, Type typeOfSrc, JsonSerializationContext ctx) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", src.typeId());
        if (!src.params().isEmpty())
            obj.add("params", ctx.serialize(src.params()));
        return obj;
    }

    @Override
    public ActionStep deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx)
            throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        // 兼容 "type" (序列化) 和 "typeId" (预设模板/用户手写)
        var typeElem = obj.get("typeId");
        if (typeElem == null) typeElem = obj.get("type");
        if (typeElem == null) throw new JsonParseException("ActionStep missing 'typeId' or 'type'");
        String typeId = typeElem.getAsString();
        if (!ActionRegistry.has(typeId)) {
            LittleMaidMoreAction.LOGGER.warn("[ActionStepAdapter] 未知动作类型: {} (将在执行时跳过)", typeId);
        }
        Map<String, String> p = obj.has("params")
                ? ctx.<Map<String, String>>deserialize(obj.get("params"), PARAMS_TYPE)
                : Map.of();
        return new ActionStep(typeId, p);
    }
}
