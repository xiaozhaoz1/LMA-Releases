package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.LmaFakePlayer;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.LmaPlayerSimulator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * AI 工具: 右键交互实体 (v73) — 指定 entity_id, 用 LmaFakePlayer 执行
 * entity.interact (LmaPlayerSimulator:111 同款实体交互路径)。
 * 权限: AI 操控任务开启。
 */
public final class InteractEntityTool implements ITool<InteractEntityTool.Result> {

    private static final String ENTITY_ID = "entity_id";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf(ENTITY_ID).forGetter(Result::entityId)
    ).apply(i, Result::new));

    @Override public String id() { return "interact_entity"; }

    @Override public String summary(EntityMaid maid) {
        return "Right-click interact with the entity with the given entity id (trade with villagers, mount, etc).";
    }

    @Override public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(ENTITY_ID, IntegerParameter.create().setDescription("Target entity id"));
        return root;
    }

    @Override public Codec<Result> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (!(maid.level() instanceof ServerLevel level)) {
            return callback.addToolResult("Failed: not on server", toolCallId);
        }
        Entity entity = level.getEntity(result.entityId());
        if (entity == null || !entity.isAlive()) {
            return callback.addToolResult("Entity %d not found or dead".formatted(result.entityId()), toolCallId);
        }
        LmaFakePlayer fp = new LmaFakePlayer(level, maid, maid.blockPosition());
        try {
            boolean consumed = entity.interact(fp, InteractionHand.MAIN_HAND).consumesAction();
            LmaPlayerSimulator.syncHandToMaid(fp);
            return callback.addToolResult(consumed
                    ? "Interacted with entity %d (%s)".formatted(result.entityId(), entity.getName().getString())
                    : "Interact with entity %d had no effect".formatted(result.entityId()), toolCallId);
        } finally {
            LmaPlayerSimulator.cleanup(fp, level);   // 样板一致性: syncHand → cleanup (FakePlayerInteract 同款)
        }
    }

    @Override
    public boolean trigger(EntityMaid maid,
                           com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion chatCompletion) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid);
    }

    public record Result(int entityId) {}
}
