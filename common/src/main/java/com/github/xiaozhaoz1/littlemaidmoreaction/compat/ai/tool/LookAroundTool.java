package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * AI 工具: 语义网格 (v77.6) — 女仆周围字符地形图 (LookAroundGrid),
 * 每格一个方块, 移动可行性语义 (LLM 空间推理)。
 */
public final class LookAroundTool implements ITool<LookAroundTool.Result> {

    private static final String RADIUS = "radius";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf(RADIUS, 8).forGetter(Result::radius)
    ).apply(i, Result::new));

    @Override public String id() { return "look_around"; }

    @Override public String summary(EntityMaid maid) {
        return "Render a top-down character map of blocks around you (terrain, walls, water, lava, "
                + "ledges). Each cell is one block: . flat, ^ step-up 1, , step-down 1-2, v drop, "
                + "# wall, ~ water, ! lava, x caution. Call ONCE to grasp terrain before planning a route.";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(RADIUS, IntegerParameter.create().setDescription("Half-width in blocks (4-16, default 8)"), false);
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (!(maid.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
            return callback.addToolResult("服务端不可用", toolCallId);
        }
        int radius = Math.max(4, Math.min(16, result.radius()));
        String grid = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.LookAroundGrid.render(
                sl, maid.blockPosition(), radius);
        return callback.addToolResult(grid, toolCallId);
    }

    public record Result(int radius) {}
}
