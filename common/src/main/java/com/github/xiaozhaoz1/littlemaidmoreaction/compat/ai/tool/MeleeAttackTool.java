package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * AI 工具: 近战攻击 (v73) — 设 ATTACK_TARGET 记忆 (女仆 AI 持续追击, SwitchWorkTaskTool
 * 同款模式) + 立即一击 (CombatOutput.damage)。权限: AI 操控任务开启。
 */
public final class MeleeAttackTool implements ITool<MeleeAttackTool.Result> {

    private static final String ENTITY_ID = "entity_id";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf(ENTITY_ID).forGetter(Result::entityId)
    ).apply(i, Result::new));

    @Override public String id() { return "melee_attack"; }

    @Override public String summary(EntityMaid maid) {
        return "Attack the entity with the given entity id with melee. The maid will keep attacking it.";
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
        if (!(level.getEntity(result.entityId()) instanceof LivingEntity target) || !target.isAlive()) {
            return callback.addToolResult("Entity %d not found or dead".formatted(result.entityId()), toolCallId);
        }
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        // 立即一击 — 伤害取女仆攻击力属性 (与面板一致, 非硬编码)
        float dmg = (float) maid.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        CombatOutput.damage(target, maid, Math.max(1.0F, dmg));
        return callback.addToolResult("Attacking %s".formatted(target.getName().getString()), toolCallId);
    }

    @Override
    public boolean trigger(EntityMaid maid,
                           com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion chatCompletion) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid);
    }

    public record Result(int entityId) {}
}
