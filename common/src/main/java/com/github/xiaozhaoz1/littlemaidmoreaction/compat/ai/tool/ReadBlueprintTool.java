package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * AI 工具: 读蓝图 (v77.6) — BlueprintReader, 蓝图尺寸/用料/分层 (不动世界一格)。
 * 蓝图文件: config/littlemaidmoreaction/blueprints/&lt;name&gt;.json
 */
public final class ReadBlueprintTool implements ITool<ReadBlueprintTool.Result> {

    private static final String NAME = "name";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf(NAME).forGetter(Result::name)
    ).apply(i, Result::new));

    @Override public String id() { return "read_blueprint"; }

    @Override public String summary(EntityMaid maid) {
        return "Read a blueprint file (dimensions, materials, per-layer breakdown) without touching the world. "
                + "Blueprints live in config/littlemaidmoreaction/blueprints/. Available: "
                + com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.BlueprintReader.availableNames(
                        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.CONFIG_DIR.resolve("blueprints"));
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(NAME, StringParameter.create().setDescription("Blueprint name (without .json)"));
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        var file = com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.CONFIG_DIR
                .resolve("blueprints").resolve(result.name() + ".json");
        var info = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.BlueprintReader.load(file);
        if (info == null) {
            return callback.addToolResult("蓝图不存在或格式错误: " + result.name(), toolCallId);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("蓝图 ").append(info.name()).append(": ")
                .append(info.sizeX()).append("x").append(info.sizeY()).append("x").append(info.sizeZ())
                .append(" (长x高x宽)\n用料: ");
        for (var e : info.materials().entrySet()) {
            sb.append(e.getValue()).append("x").append(e.getKey()).append(", ");
        }
        sb.append("\n按层: ");
        for (String layer : info.layers()) {
            sb.append(layer).append("; ");
        }
        return callback.addToolResult(sb.toString(), toolCallId);
    }

    public record Result(String name) {}
}
