package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.MaidStateReader;
import com.mojang.serialization.Codec;

/**
 * AI 工具: 自身状态 (v73) — 只读感知 (无权限门控, TLM 内置只读工具同款惯例)。
 * 聚合 MaidStateReader: 生命/饥饿/好感/经验/位置/维度/任务/背包。
 */
public final class GetSelfStatusTool implements ITool<GetSelfStatusTool.Result> {

    private static final Codec<Result> CODEC = Codec.unit(new Result());

    @Override public String id() { return "get_self_status"; }

    @Override public String summary(EntityMaid maid) {
        return "Get the maid's current status: health, hunger, favorability, experience, position, dimension, current task, backpack.";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) { return root; }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        String status = String.format("""
                Health: %.1f/%.1f
                Hunger: %d
                Favorability: %d
                Experience: %d
                Position: (%.1f, %.1f, %.1f)
                Dimension: %s
                Current task: %s
                Pickup enabled: %s
                Has backpack: %s
                """,
                MaidStateReader.getHealth(maid), MaidStateReader.getMaxHealth(maid),
                MaidStateReader.getHunger(maid), MaidStateReader.getFavorability(maid),
                MaidStateReader.getExperience(maid),
                maid.getX(), maid.getY(), maid.getZ(),
                maid.level().dimension().location(),
                MaidStateReader.getTaskUid(maid),
                MaidStateReader.isPickup(maid),
                MaidStateReader.hasBackpack(maid));
        return callback.addToolResult(status, toolCallId);
    }

    public record Result() {}
}
