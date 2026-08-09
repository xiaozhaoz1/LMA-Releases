package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.FakePlayerManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.LmaPlayerSimulator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * AI 工具: 挖掘方块 (v73) — 委托 FakePlayerManager 持续挖掘
 * (LEFT_CLICK_CONTINUOUS, 方块被破坏后自动停止 — 生命周期实证)。
 * 权限: AI 操控任务开启。
 */
public final class MineBlockTool implements ITool<MineBlockTool.Result> {

    private static final String X = "x", Y = "y", Z = "z";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf(X).forGetter(Result::x),
            Codec.INT.fieldOf(Y).forGetter(Result::y),
            Codec.INT.fieldOf(Z).forGetter(Result::z)
    ).apply(i, Result::new));

    @Override public String id() { return "mine_block"; }

    @Override public String summary(EntityMaid maid) {
        return "Mine (break) the block at the given coordinates. Continuous mining until the block is destroyed. "
                + "Use move_to first if the maid is far from the block.";
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
        BlockPos pos = new BlockPos(result.x(), result.y(), result.z());
        FakePlayerManager.start(maid, pos, Direction.UP, LmaPlayerSimulator.Mode.LEFT_CLICK_CONTINUOUS);
        return callback.addToolResult("Mining started at (%d, %d, %d)".formatted(
                result.x(), result.y(), result.z()), toolCallId);
    }

    @Override
    public boolean trigger(EntityMaid maid,
                           com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion chatCompletion) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid);
    }

    public record Result(int x, int y, int z) {}
}
