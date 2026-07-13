package littlemaidmoreaction.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;

import java.util.*;

/**
 * AI 文档工具 (v12 P4 → v16): 按主题返回领域文档。
 */
public final class LmaDocsTool implements ITool<String> {

    private static final Codec<String> CODEC = Codec.STRING.optionalFieldOf("_", "").codec();

    private static final Map<String, String> DOCS = new LinkedHashMap<>();
    static {
        DOCS.put("basics",
            """
            === IDENTITY ===
            You ARE this Touhou Little Maid. You are talking to your owner.
            - OWNER = the player talking to you right now. Says "I/me/my" = owner.
              When owner says "protect me" or "give me food" → target=owner.
            - YOU = this maid (the entity you control). Says nothing directly.
              When owner says "attack that zombie" → you (the maid) should attack.
            - target=self = you (this maid). target=owner = the player (your owner).
            - target=target = the event target (enemy in combat, entity in event).

            === WHEN OWNER GIVES A TASK COMMAND ===
            ☆ DO THIS FIRST: directly call lma_start_task with the matching task_type.
            ☆ DO NOT query inventory/equipment/items — the task system handles everything internally.
            ☆ Task keyword → task_type mapping:
              "craft X" / "make X" / "合成X" → craft_chain
              "smelt X" / "烧X" → furnace
              "ring bell" / "敲钟" → bell_ring
              "play music" / "放音乐" → jukebox
              "do altar" / "祭坛" → altar_craft
              (brewing is DELETED — maid cannot brew potions)
            ☆ Example:
              lma_start_task(task_type="craft_chain", target="minecraft:stick", target_count=16)
            ☆ The task system auto-drives everything — you just call lma_start_task.

            === EVENT SYSTEM ===
            LMA rules are triggered by events. Call lma_guide("combat") for combat events.
            Events with (cancellable) can be stopped with cancel_event action.
            """);

        DOCS.put("combat",
            """
            === MAID ATTACKS TARGET (dealing damage to enemy) ===
            maid_hurt_target_pre  — BEFORE hit connects. Use for: combos, cancel to prevent normal attack.
                                     Cancel + play_anim + deal_damage = custom attack animation.
                                     This is the "maid is attacking someone" event. (cancellable)
            maid_hurt_target_post — AFTER hit connects. Use for: follow-up effects, kill checks.
                                     NOT cancellable (damage already dealt).

            === MAID IS ATTACKED (taking damage from enemy) ===
            These 4 events fire SEQUENTIALLY when maid takes damage:
              maid_attack  — BEFORE enemy hit lands. Use for: dodge, parry, block. (cancellable)
                             IMPORTANT: "maid_attack" = maid IS attacked, NOT maid attacks!
              maid_hurt    — damage CALCULATION. Modify damage amount here. (cancellable)
              maid_damage  — damage APPLIED (final). For post-damage reactions. (cancellable)
              maid_death   — maid about to DIE. Use for: revive, death prevention. (cancellable)

            === COMMON COMBAT PATTERNS ===
            Dodge:     maid_attack → cancel_event + teleport(offset=2) + play_anim(INSTANT, dodge)
            Parry:     maid_attack → cancel_event + deal_damage + play_anim(INSTANT, counter)
            Combo:     maid_hurt_target_pre → cancel_event + play_anim(FULL, combo_a1, auto_wait)
            Execution: maid_hurt_target_pre → is_holding_katana + would_lethal → cancel_event + execution_kill
            Revenge:   maid_damage → damage_nearby(enemy) + play_sound(wither.shoot)
            """);

        DOCS.put("task",
            """
            === v16 TASK SYSTEM (REWRITTEN) ===
            Pipeline-driven task execution. AI calls lma_start_task ONCE — system handles everything.

            === CREATING A TASK ===
            1. Call lma_start_task(task_type, target, target_count)
               → System auto-creates rule (event=task_changed) from preset template
               → Pipeline checks materials, writes rule, drives execution
            2. That's it — no need to create rules or query recipes

            === TASK TYPES ===
            craft_chain  — crafting table multi-step synthesis (e.g. logs→planks→sticks)
            furnace      — smelt/cook items (auto fuel+input+output loop)
            jukebox      — play music discs (auto insert/eject/rotate)
            bell_ring    — ring nearby bell
            altar_craft  — Touhou Little Maid altar crafting

            === target_count PARAMETER ===
            -1 = unlimited (make until materials run out) — DEFAULT
            32 = make exactly 32 (only when owner says exact number)
            64 = make one stack

            ☆ Never query inventory, recipes, or try to create rules manually.
            ☆ Just call lma_start_task with the right task_type and target.
            """);

        DOCS.put("target",
            """
            === TARGET SYSTEM ===
            Most actions have a "target" parameter. Values:
              "self"   — yourself, this maid (for self-buff, self-heal, self-teleport)
              "target" — the event target (enemy in combat, player in interaction, entity in event)
              "owner"  — your owner, the player talking to you

            === EXAMPLES ===
            Heal owner:       heal(amount=10, target=owner)
            Maid self-buff:   apply_effect(effect=speed, target=self)
            Attack enemy:     deal_damage(amount=10, target=target)
            Teleport to self: teleport(mode=self, ...) — maid teleports

            === BLOCK TARGETS ===
            Block actions (break_block, interact_block, place_altar_item) use offset_xyz
            and search range, not "target". They find blocks near the maid automatically.

            === ENTITY TARGETS ===
            For entity actions: the "target" field resolves using EngineUtils.resolveTarget():
            - In combat events (maid_hurt_target_pre/post): target = the entity maid is attacking
            - In defense events (maid_attack/hurt/damage): target = the attacker (DamageSource.getEntity())
            - In interaction events (maid_interact): target = the player who right-clicked
            """);
    }

    @Override public String id() { return "lma_guide"; }

    @Override
    public String summary(EntityMaid maid) {
        return """
            Get documentation about LMA systems. Call BEFORE creating rules for a domain.
            Topics: combat (attack/defense phases), task (v16 flow task architecture),
            target (self/target/owner meanings), basics (identity, event overview).
            """.strip();
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        var topic = StringParameter.create()
            .setDescription("Documentation topic to retrieve");
        for (String t : DOCS.keySet()) topic.addEnumValues(t);
        root.addProperties("topic", topic);
        return root;
    }

    @Override public Codec<String> codec() { return CODEC; }

    @Override
    public LLMCallback onCall(String toolCallId, String topic, LLMCallback cb) {
        String doc = DOCS.get(topic);
        if (doc != null) return cb.addToolResult(doc.trim(), toolCallId);

        StringBuilder sb = new StringBuilder("Unknown topic '").append(topic)
            .append("'. Available: ");
        sb.append(String.join(", ", DOCS.keySet()));
        sb.append("\n\nTip: Start with lma_guide(\"basics\") for an overview.");
        return cb.addToolResult(sb.toString(), toolCallId);
    }

    @Override public String invocationSummary(String t) { return "lma_guide { " + t + " }"; }
}
