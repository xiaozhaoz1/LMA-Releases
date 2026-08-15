package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi;
import net.minecraft.core.BlockPos;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * AI 工具: 移动 (v73/v78 Phase 3) — 女仆寻路到指定坐标。
 *
 * <p>v79.23 升级: 弃自研 PathExecutor → 走路全 TLM 导航 (setWalkAndLookTargetMemories),
 * 零自研引擎 (maid_useful_task 模式)。TLM 不挖方块 — mode 语义保留:
 * safe=仅走/绕, explorer=TLM 可达路径 (挖/搭由采集任务 3D 挖穿面负责)。
 * 驱动: TLM Brain 自动行走, 无需 sweep。权限: AI 操控任务开启 (trigger 门控)。
 */
public final class MoveToTool implements GatedMaidTool<MoveToTool.Result> {

    private static final String X = "x", Y = "y", Z = "z", MODE = "mode";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf(X).forGetter(Result::x),
            Codec.INT.fieldOf(Y).forGetter(Result::y),
            Codec.INT.fieldOf(Z).forGetter(Result::z),
            Codec.STRING.fieldOf(MODE).orElse("safe").forGetter(Result::mode)
    ).apply(i, Result::new));

    @Override public String id() { return "move_to"; }

    @Override public String summary(EntityMaid maid) {
        return "Move the maid to the given block coordinates (x, y, z). "
                + "mode=safe only walks around obstacles (never breaks blocks); "
                + "mode=explorer may dig through, bridge gaps and parkour. "
                + "Use when the user asks the maid to go somewhere.";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(X, IntegerParameter.create().setDescription("Target block X"));
        root.addProperties(Y, IntegerParameter.create().setDescription("Target block Y"));
        root.addProperties(Z, IntegerParameter.create().setDescription("Target block Z"));
        root.addProperties(MODE, StringParameter.create().setDescription("safe (no block breaking) or explorer (dig/bridge/parkour), default safe"));
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        // 走路全 TLM (零自研引擎) — 幂等 navigateTo 写 WALK_TARGET, TLM Brain 自动行走
        PathingApi.navigateTo(maid, new BlockPos(result.x(), result.y(), result.z()), 0.5F);
        String mode = "explorer".equals(result.mode()) ? "explorer" : "safe (no breaking)";
        return callback.addToolResult("Pathfinding started to (%d, %d, %d) mode=%s".formatted(
                result.x(), result.y(), result.z(), mode), toolCallId);
    }

    public record Result(int x, int y, int z, String mode) {}
}
