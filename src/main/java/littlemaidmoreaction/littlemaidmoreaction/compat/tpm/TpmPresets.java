package littlemaidmoreaction.littlemaidmoreaction.compat.tpm;

import littlemaidmoreaction.littlemaidmoreaction.core.model.MatchMode;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import java.util.List;

/**
 * TPM 兼容预设 — ID 范围 240-259。
 * <p>所有预设依赖 {@code true_power_of_maid + slashblade} 双模组。
 */
public final class TpmPresets {
    public static List<RuleDef> createDefaults() {
        return List.of(
            // ═══ 真之力反制 (240-241) ═══
            f(240, "TPM-真之力反击", "maid_attack", 1.0, 100, 80,
                L(c("tpm_has_true_power"), c("maid_has_weapon")),
                a("cancel_event"),
                a("slashblade_sa","knockback","toss","damage_mult","1.5"),
                a("break")),
            f(241, "TPM-无限剑制", "maid_attack", 0.5, 300, 90,
                L(c("tpm_has_ubw"), c("maid_has_weapon")),
                a("cancel_event"),
                a("slashblade_sa","knockback","smash","damage_mult","2.0"),
                a("play_sound","sound_id","minecraft:entity.wither.shoot","volume","0.5"),
                a("break")),

            // ═══ 格挡系统 (242-243) ═══
            f(242, "TPM-自动格挡", "maid_attack", 1.0, 100, 70,
                L(c("tpm_has_guard"), c("tpm_guard_cooldown", ":=:", "0")),
                a("cancel_event"),
                a("tpm_force_guard"),
                a("play_sound","sound_id","minecraft:item.shield.block","volume","1.0"),
                a("break")),
            f(243, "TPM-格挡反制", "maid_hurt_target_post", 1.0, 60, 65,
                L(c("tpm_has_guard"), c("tpm_is_guarding")),
                a("slashblade_sa","knockback","smash","damage_mult","1.3"),
                a("break")),

            // ═══ 虚空斩连携 (244) ═══
            f(244, "TPM-虚空斩杀", "maid_hurt_target_pre", 0.3, 300, 85,
                L(c("tpm_has_void_slash"), c("damage_type",":=:","melee"), c("would_lethal")),
                a("cancel_event"),
                a("slashblade_sa","knockback","smash","damage_mult","2.5"),
                a("play_sound","sound_id","minecraft:entity.wither.shoot","volume","0.6"),
                a("break")),

            // ═══ 饰品联动示例 (245-246) ═══
            f(245, "TPM-瞬移闪避", "maid_attack", 0.3, 60, 55,
                L(c("tpm_has_trick"), c("damage_type",":=:","ranged")),
                a("cancel_event"),
                a("teleport","target","self","mode","offset","offset_x","0.8"),
                a("break")),
            f(246, "TPM-经验修拔刀", "maid_tick", 1.0, 60, 5,
                L(c("tpm_has_exp")),
                a("repair_katana"))
        );
    }
    @SafeVarargs private static <T> List<T> L(T... items) { return List.of(items); }
    private static RuleDef f(int id, String n, String ev, double prob, int cd, int pri, List<ConditionDef> conds, ActionStep... acts) {
        return RuleDef.full(id, n, ev, prob, cd, pri, MatchMode.ALL, conds, List.of(acts), List.of("true_power_of_maid","slashblade"));
    }
    private static ConditionDef c(String k, String o, String v) { return new ConditionDef(k, o, v); }
    private static ConditionDef c(String k) { return new ConditionDef(k); }
    private static ActionStep a(String type, String... kv) { return ActionStep.of(type, kv); }
}
