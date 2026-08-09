package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;

/**
 * AI 工具: 世界状态 (v77.6) — 维度/tick/昼夜/天气 (WorldStateReader)。
 */
public final class GetWorldInfoTool implements ITool<GetWorldInfoTool.Result> {

    @Override public String id() { return "get_world_info"; }

    @Override public String summary(EntityMaid maid) {
        return "Read current world state: dimension, game tick, day/night, weather (clear/rain/thunder). No arguments.";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        return root;
    }

    @Override public Codec<Result> codec() {
        return Codec.unit(new Result());
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (!(maid.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
            return callback.addToolResult("服务端不可用", toolCallId);
        }
        return callback.addToolResult(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.WorldStateReader.describe(
                        sl, maid.blockPosition()), toolCallId);
    }

    public record Result() {}
}
