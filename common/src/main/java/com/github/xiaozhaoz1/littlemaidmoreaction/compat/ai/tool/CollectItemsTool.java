package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.List;

/**
 * AI 工具: 收集掉落物 (v73) — 感知并报告附近掉落物; 女仆拾取由 TLM pickup 能力负责
 * (v1 感知版 — 返回数量+位置提示, 引导女仆拾取/移动)。
 * 权限: AI 操控任务开启。
 */
public final class CollectItemsTool implements ITool<CollectItemsTool.Result> {

    private static final String RADIUS = "radius";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf(RADIUS, 8).forGetter(Result::radius)
    ).apply(i, Result::new));

    @Override public String id() { return "collect_items"; }

    @Override public String summary(EntityMaid maid) {
        return "Scan nearby item drops and report their count and positions. The maid will pick them up.";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(RADIUS, IntegerParameter.create().setDescription("Scan radius (default 8)"), false);
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        int radius = Math.max(1, Math.min(result.radius(), 32));
        List<ItemEntity> items = maid.level().getEntitiesOfClass(ItemEntity.class,
                maid.getBoundingBox().inflate(radius));
        if (items.isEmpty()) {
            return callback.addToolResult("No item drops found within %d blocks".formatted(radius), toolCallId);
        }
        long total = items.stream().mapToLong(e -> e.getItem().getCount()).sum();
        return callback.addToolResult("Found %d item drops (total %d items) within %d blocks. Maid pickup enabled: %s"
                .formatted(items.size(), total, radius, com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.MaidStateReader.isPickup(maid)),
                toolCallId);
    }

    @Override
    public boolean trigger(EntityMaid maid,
                           com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion chatCompletion) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid);
    }

    public record Result(int radius) {}
}
