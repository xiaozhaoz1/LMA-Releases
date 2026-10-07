package com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlockEntity;

/**
 * 防御塔(手办)动画名**护栏** (v79.66q, 纯函数 ✓ 可单测)。
 *
 * <p><b>为什么需要</b> (实机实测 2026-09-21): 配置里残留历史默认值
 * {@code aim_anim = "iss:charge_arrow"} / {@code fire_anim = "iss:instant_projectile"} ✗ ——
 * 这两个是 **ISS 施法(弹幕)动画**, 而塔的**瞄准动画由"有目标"直接触发**
 * (见 {@code DefenseGarageKitBlockEntity}: {@code if (hasTarget) beginAimAnim();} — 与武器无关 ✗)
 * ⇒ 手办会持续播"施法动作", 且与"模型被替换"的现象同时出现 ✓。
 *
 * <p>⇒ 现在**任何以 {@code iss:} 开头的动画名在塔(手办)上一律拒绝**, 回退安全默认 ✓:
 * <ul>
 *   <li>瞄准 → {@link #AIM_SAFE_DEFAULT} = {@code use_mainhand:bow} (TLM 原版拉弓 ✓)</li>
 *   <li>开火 → {@link #FIRE_SAFE_DEFAULT} = 空 (只"松手", 与真实女仆一致 ✓)</li>
 * </ul>
 * ⚠ 配置里**写空** = 用户显式关闭动画 ⇒ 尊重原样 (不替换) ✓。
 */
public final class DefenseTowerAnimNames {

    private DefenseTowerAnimNames() {}

    /** 瞄准安全默认 (TLM 原版模型拉弓 ✓) */
    public static final String AIM_SAFE_DEFAULT = "use_mainhand:bow";
    /** 开火安全默认 = 空 (不写动画 ⇒ 保持拉弓 = 真实女仆"松手"表现 ✓) */
    public static final String FIRE_SAFE_DEFAULT = "";

    /** 施法/弹幕动画前缀 — 塔(手办)禁用: 会播施法动作 ✗ */
    public static final String SPELL_PREFIX = "iss:";

    /** 是否施法动画 (塔禁用 ✓) */
    public static boolean isSpellAnim(String name) {
        return name != null && name.startsWith(SPELL_PREFIX);
    }

    /** 取安全的**瞄准**动画名 (施法 ⇒ 回退 {@link #AIM_SAFE_DEFAULT} ✓; 空 ⇒ 保持空 ✓) */
    public static String safeAim(String configured) {
        if (configured == null || configured.isBlank()) return FIRE_SAFE_DEFAULT;   // 显式关 = 尊重 ✓
        return isSpellAnim(configured) ? AIM_SAFE_DEFAULT : configured;
    }

    /** 取安全的**开火**动画名 (施法 ⇒ 回退 {@link #FIRE_SAFE_DEFAULT} = 空 ✓; 空 ⇒ 保持空 ✓) */
    public static String safeFire(String configured) {
        if (configured == null || configured.isBlank()) return FIRE_SAFE_DEFAULT;
        return isSpellAnim(configured) ? FIRE_SAFE_DEFAULT : configured;
    }
}
