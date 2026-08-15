package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.FakePlayerInteract;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * AI 工具: 右键交互方块 (v73) — 委托 FakePlayerInteract.rightClick (完整右键管线:
 * 事件后门 + useOn + 掉落入背包)。权限: AI 操控任务开启。
 */
public final class InteractBlockTool implements GatedMaidTool<InteractBlockTool.Result> {

    private static final String X = "x", Y = "y", Z = "z";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf(X).forGetter(Result::x),
            Codec.INT.fieldOf(Y).forGetter(Result::y),
            Codec.INT.fieldOf(Z).forGetter(Result::z)
    ).apply(i, Result::new));

    @Override public String id() { return "interact_block"; }

    @Override public String summary(EntityMaid maid) {
        return "Right-click interact with the block at the given coordinates (open chests, use levers, etc).";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(X, IntegerParameter.create().setDescription("Block X"));
        root.addProperties(Y, IntegerParameter.create().setDescription("Block Y"));
        root.addProperties(Z, IntegerParameter.create().setDescription("Block Z"));
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (!(maid.level() instanceof ServerLevel level)) {
            return callback.addToolResult("Failed: not on server", toolCallId);
        }
        BlockPos pos = new BlockPos(result.x(), result.y(), result.z());
        // 统一走全局右键门面 (获得距离检查, 与 BlockInteractPipeline 一致)
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.BlockInteractService.interact(level, maid, pos);
        return callback.addToolResult(ok
                ? "Interacted with block at (%d, %d, %d)".formatted(result.x(), result.y(), result.z())
                : "Interact failed at (%d, %d, %d)".formatted(result.x(), result.y(), result.z()), toolCallId);
    }

    public record Result(int x, int y, int z) {}
}
