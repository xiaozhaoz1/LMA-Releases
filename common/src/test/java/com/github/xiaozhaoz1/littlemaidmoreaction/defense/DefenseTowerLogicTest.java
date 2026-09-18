package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefenseTowerLogic 纯函数测试 (v79.62.3) — 索敌/冷却/数值 clamp。
 * 纯 JVM (不触发 MC 类加载 — LivingEntity 仅作 null 参数传入, 不实例化)。
 */
class DefenseTowerLogicTest {

    @Test
    @DisplayName("半径 clamp 4..64 边界")
    void clampRadius_bounds() {
        assertEquals(4, DefenseTowerLogic.clampRadius(-5));
        assertEquals(4, DefenseTowerLogic.clampRadius(0));
        assertEquals(16, DefenseTowerLogic.clampRadius(16));
        assertEquals(64, DefenseTowerLogic.clampRadius(100));
    }

    @Test
    @DisplayName("伤害 clamp 0.5..100 边界")
    void clampDamage_bounds() {
        assertEquals(0.5, DefenseTowerLogic.clampDamage(-1));
        assertEquals(2.0, DefenseTowerLogic.clampDamage(2.0));
        assertEquals(100.0, DefenseTowerLogic.clampDamage(999));
    }

    @Test
    @DisplayName("射击节奏 clamp 10..200 边界")
    void clampFireInterval_bounds() {
        assertEquals(10, DefenseTowerLogic.clampFireInterval(0));
        assertEquals(20, DefenseTowerLogic.clampFireInterval(20));
        assertEquals(200, DefenseTowerLogic.clampFireInterval(1000));
    }

    @Test
    @DisplayName("冷却判定: 时钟回绕守卫 (last > now 放行)")
    void isReady_clockRollback() {
        assertTrue(DefenseTowerLogic.isReady(100, 0, 20));     // 首次开火
        assertFalse(DefenseTowerLogic.isReady(100, 95, 20));   // 冷却中
        assertTrue(DefenseTowerLogic.isReady(100, 80, 20));    // 冷却结束
        assertTrue(DefenseTowerLogic.isReady(100, 200, 20));   // 时钟回绕
    }

    @Test
    @DisplayName("攻击方式 byId: 未知值回落 NONE (无武器不开火)")
    void shootMode_byId() {
        assertEquals(DefenseTowerLogic.ShootMode.NONE, DefenseTowerLogic.ShootMode.byId(-1));
        assertEquals(DefenseTowerLogic.ShootMode.ARROW, DefenseTowerLogic.ShootMode.byId(0));
        assertEquals(DefenseTowerLogic.ShootMode.DANMAKU, DefenseTowerLogic.ShootMode.byId(1));
        assertEquals(DefenseTowerLogic.ShootMode.NONE, DefenseTowerLogic.ShootMode.byId(99));
    }

    @Test
    @DisplayName("索敌: null/空列表返回 null")
    void nearestHostile_empty() {
        assertNull(DefenseTowerLogic.nearestHostile(null, new Vec3(0, 0, 0), 256));
        assertNull(DefenseTowerLogic.nearestHostile(new ArrayList<>(), new Vec3(0, 0, 0), 256));
    }
    // ── 相位抖动 (v79.63, 错题 #277) ──

    @Test
    @DisplayName("相位: 由坐标派生且稳定 (同坐标恒同相位, 无需持久化)")
    void phaseOf_stable() {
        int p1 = DefenseTowerLogic.phaseOf(100, -59, 85, 20);
        int p2 = DefenseTowerLogic.phaseOf(100, -59, 85, 20);
        assertEquals(p1, p2, "同坐标必须恒同相位 (卸载重载后行为不变)");
        assertTrue(p1 >= 0 && p1 < 20, "相位必须落在 0..period-1");
    }

    @Test
    @DisplayName("相位: 量化到吸收窗口整数倍 (period=20 → 仅 0/10 两槽)")
    void phaseOf_quantizedToAbsorbWindow() {
        for (int x = -512; x <= 512; x += 7) {
            int p = DefenseTowerLogic.phaseOf(x, -59, x * 3, 20);
            assertEquals(0, p % DefenseTowerLogic.ABSORB_WINDOW,
                    "相位必须是 ABSORB_WINDOW 的整数倍 (否则相位差<10 时两塔仍互吞): x=" + x + " p=" + p);
        }
    }

    @Test
    @DisplayName("相位: period ≤ 吸收窗口时恒 0 (无法错开, 不抛异常)")
    void phaseOf_degeneratePeriod() {
        assertEquals(0, DefenseTowerLogic.phaseOf(1, 2, 3, 10));
        assertEquals(0, DefenseTowerLogic.phaseOf(1, 2, 3, 1));
        assertEquals(0, DefenseTowerLogic.phaseOf(1, 2, 3, 0));
    }

    @Test
    @DisplayName("相位闸门: 每 period 恰好命中一次, 且不同相位错开")
    void isPhaseTick_slotsDisjoint() {
        int period = 20;
        int phaseA = 0, phaseB = 10;   // period=20 的两槽
        int hitA = 0, hitB = 0;
        for (long t = 0; t < 200; t++) {
            if (DefenseTowerLogic.isPhaseTick(t, phaseA, period)) hitA++;
            if (DefenseTowerLogic.isPhaseTick(t, phaseB, period)) hitB++;
            assertFalse(DefenseTowerLogic.isPhaseTick(t, phaseA, period)
                            && DefenseTowerLogic.isPhaseTick(t, phaseB, period),
                    "两槽不得同 tick 触发 (否则齐射依旧): t=" + t);
        }
        assertEquals(10, hitA, "相位 0: 200t 内应恰好 10 次");
        assertEquals(10, hitB, "相位 10: 200t 内应恰好 10 次");
    }

    @Test
    @DisplayName("相位闸门: 时钟回绕/大数值不抛异常")
    void isPhaseTick_wraps() {
        assertTrue(DefenseTowerLogic.isPhaseTick(0L, 0, 20));
        assertTrue(DefenseTowerLogic.isPhaseTick(Long.MAX_VALUE - 1, DefenseTowerLogic.phaseOf(3, 4, 5, 20), 20)
                || true);   // 只要求不抛 (floorMod 语义, 与 isReady 的守卫不同)
        assertTrue(DefenseTowerLogic.isPhaseTick(12345L, 0, 1), "period<=1 恒真");
    }

    // ── 弹道纯函数 (v79.63 自 BE.fireArrow 抽出) ──

    @Test
    @DisplayName("下坠补偿: 距离 0 → 0, 且随距离单调增 (24 格 = 1.6 格)")
    void ballisticDrop_monotonic() {
        assertEquals(0.0, DefenseTowerLogic.ballisticDrop(0), 1e-9);
        // 公式: 0.5 * 0.05 * (dist/3)²  ⇒ 24 格 → 0.5*0.05*64 = 1.6
        assertEquals(1.6, DefenseTowerLogic.ballisticDrop(24), 1e-9);
        assertEquals(0.5444, DefenseTowerLogic.ballisticDrop(14), 1e-3);
        double prev = -1;
        for (double d = 0; d <= 64; d += 4) {
            double drop = DefenseTowerLogic.ballisticDrop(d);
            assertTrue(drop > prev, "下坠应随距离单调增: d=" + d);
            prev = drop;
        }
    }

    @Test
    @DisplayName("瞄准向量: 水平射击时向上补偿 = 下坠量 (瞄准眼位裁定)")
    void aimAt_levelShot_compensatesUpward() {
        Vec3 origin = new Vec3(0, 2.68, 0);
        Vec3 eye = new Vec3(0, 2.68, 24);          // 同高度, 正前方 24 格
        Vec3 dir = DefenseTowerLogic.aimAt(origin, eye);
        assertEquals(0.0, dir.x, 1e-9, "正前方 X 无偏移");
        assertEquals(24.0, dir.z, 1e-9, "Z 指向目标");
        assertEquals(1.6, dir.y, 1e-9, "Y 应抬到下坠补偿量 (1.6)");
    }

    @Test
    @DisplayName("瞄准向量: 目标低于塔口 → 下压; 高于塔口 → 上抬")
    void aimAt_verticalSign() {
        Vec3 origin = new Vec3(0, 10, 0);
        Vec3 below = DefenseTowerLogic.aimAt(origin, new Vec3(0, 5, 12));
        assertTrue(below.y < 0, "目标在下方 → 下压: " + below.y);
        Vec3 above = DefenseTowerLogic.aimAt(origin, new Vec3(0, 15, 12));
        assertTrue(above.y > 0, "目标在上方 → 上抬: " + above.y);
    }

    @Test
    @DisplayName("瞄准向量: 用 3D 直线距离估飞行时间 (含高度差), 与实测偏差口径一致")
    void aimAt_uses3dDistance() {
        Vec3 origin = new Vec3(0, 0, 0);
        Vec3 eye = new Vec3(0, 10, 0);             // 纯垂直 10 格
        Vec3 dir = DefenseTowerLogic.aimAt(origin, eye);
        double expectedDrop = DefenseTowerLogic.ballisticDrop(10);
        assertEquals(10 + expectedDrop, dir.y, 1e-9, "垂直射击也要补偿 (按 3D 距离)");
    }

    // ── v79.64.4: 箭塔伤害决策 (双平台共享; 修 neo 漏配) ──

    @Test
    @DisplayName("箭塔伤害优先级: 单塔覆盖 > 全局默认 > vanilla")
    void effectiveArrowDamagePriority() {
        // 有覆盖值 → 覆盖值 (即使全局更大)
        org.junit.jupiter.api.Assertions.assertEquals(7.0, DefenseTowerLogic.effectiveArrowDamage(7.0, 2.0, 3.0), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(0.5, DefenseTowerLogic.effectiveArrowDamage(0.5, 100.0, 3.0), 1e-9);
        // 无覆盖 (NaN / 0 / 负) → 全局默认
        org.junit.jupiter.api.Assertions.assertEquals(2.0, DefenseTowerLogic.effectiveArrowDamage(Double.NaN, 2.0, 3.0), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(5.0, DefenseTowerLogic.effectiveArrowDamage(0.0, 5.0, 3.0), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(5.0, DefenseTowerLogic.effectiveArrowDamage(-1.0, 5.0, 3.0), 1e-9);
        // 全局也无效 (<=0, 配置被手改) → vanilla
        org.junit.jupiter.api.Assertions.assertEquals(3.0, DefenseTowerLogic.effectiveArrowDamage(Double.NaN, 0.0, 3.0), 1e-9);
        // ★ 回归锚点: 默认配置 (全局 2.0) + 无覆盖 ⇒ 原版箭基础伤害 2.0 (行为不变)
        org.junit.jupiter.api.Assertions.assertEquals(2.0, DefenseTowerLogic.effectiveArrowDamage(Double.NaN, 2.0, 2.0), 1e-9);
    }
}
