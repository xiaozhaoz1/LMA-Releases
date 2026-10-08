package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower.DefenseTowerAnimNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防御塔动画名护栏守护 (v79.66q, 实机实测固化 ✓)。
 *
 * <p>固化三件事:
 * <ol>
 *   <li><b>施法(ISS)动画一律拒绝</b> — 配置里残留的历史默认值
 *       {@code iss:charge_arrow} / {@code iss:instant_projectile} 会让手办播"施法动作" ✗
 *       (塔的瞄准动画由「有目标」直接触发, 与武器无关) ⇒ 必须回退安全默认 ✓</li>
 *   <li><b>安全默认</b> — 瞄准 = {@code use_mainhand:bow} (TLM 原版拉弓 ✓); 开火 = 空 (只松手 ✓)</li>
 *   <li><b>显式空值要尊重</b> — 用户写空 = 主动关动画 ⇒ 不得被替换成默认 ✗</li>
 * </ol>
 */
class DefenseTowerAnimNamesTest {

    @Test
    @DisplayName("施法(iss:*)动画必须被拒绝并回退安全默认")
    void spellAnimationsAreRejected() {
        assertTrue(DefenseTowerAnimNames.isSpellAnim("iss:charge_arrow"), "iss: 前缀应判定为施法动画");
        assertTrue(DefenseTowerAnimNames.isSpellAnim("iss:instant_projectile"), "同上");
        assertFalse(DefenseTowerAnimNames.isSpellAnim("use_mainhand:bow"), "普通动画不得误判 ✗");
        assertFalse(DefenseTowerAnimNames.isSpellAnim("swing:bow"), "同上");
        assertFalse(DefenseTowerAnimNames.isSpellAnim(null), "null 不得 NPE");
        assertFalse(DefenseTowerAnimNames.isSpellAnim(""), "空串不得误判");

        // 历史残留默认值 (实机 defense_tower.toml 实录) ⇒ 必须映射到安全默认 ✓
        assertEquals(DefenseTowerAnimNames.AIM_SAFE_DEFAULT,
                DefenseTowerAnimNames.safeAim("iss:charge_arrow"), "瞄准施法动画 ⇒ use_mainhand:bow ✓");
        assertEquals(DefenseTowerAnimNames.FIRE_SAFE_DEFAULT,
                DefenseTowerAnimNames.safeFire("iss:instant_projectile"), "开火施法动画 ⇒ 空(只松手) ✓");
    }

    @Test
    @DisplayName("普通动画名原样通过; 空值保持为空 (尊重显式关闭)")
    void normalNamesPassThroughAndBlankStaysBlank() {
        assertEquals("use_mainhand:bow", DefenseTowerAnimNames.safeAim("use_mainhand:bow"), "普通名原样 ✓");
        assertEquals("swing:bow", DefenseTowerAnimNames.safeAim("swing:bow"), "普通名原样 ✓");
        assertEquals("swing:bow", DefenseTowerAnimNames.safeFire("swing:bow"), "普通名原样 ✓");

        // ⚠ 显式空 = 用户关动画 ⇒ 必须保持空 (不能被替换成 bow ✗)
        assertEquals("", DefenseTowerAnimNames.safeAim(""), "空瞄准保持空 ✓");
        assertEquals("", DefenseTowerAnimNames.safeAim("   "), "全空白保持空 ✓");
        assertEquals("", DefenseTowerAnimNames.safeAim(null), "null 视为关 ✓");
        assertEquals("", DefenseTowerAnimNames.safeFire(""), "空开火保持空 ✓");
        assertEquals("", DefenseTowerAnimNames.safeFire(null), "null 视为关 ✓");
    }
}
