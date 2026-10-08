package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 防御塔逻辑纯函数层 (v79.62.3) — 索敌/冷却/模式/半径的可测部分。
 *
 * <p>MC 实体/世界操作 (视线 raycast / 射箭 spawn / 弹幕) 在 {@link DefenseGarageKitBlockEntity}
 * tick 层执行; 本类只做判定与数值 clamp (JVM 单测覆盖, 错题 #174: 不触发 MC 类加载的静态部分)。
 */
public final class DefenseTowerLogic {

    /** 攻击方式 (由武器槽自动判定: 弓=ARROW, 御币=DANMAKU, 其他/空=NONE) */
    public enum ShootMode {
        NONE(-1), ARROW(0), DANMAKU(1);

        private final int id;

        ShootMode(int id) { this.id = id; }

        public int id() { return id; }

        public static ShootMode byId(int id) {
            if (id == DANMAKU.id) return DANMAKU;
            if (id == ARROW.id) return ARROW;
            return NONE;
        }
    }

    /** 索敌半径 clamp (全局默认 + 每塔覆盖共用一个边界 — 4..64 对齐 EntityScanCache 覆盖) */
    public static int clampRadius(int radius) {
        return Math.max(4, Math.min(64, radius));
    }

    /**
     * 箭塔**每发伤害决策** (纯函数, 双平台共享 — v79.64.4 用户裁定「补齐」).
     *
     * <p><b>为什么抽出来</b>: 原决策在两处内联 ({@code DefenseTowerFire.fireArrow} 的 1.20.1 / 1.21.1 分支),
     * 结果 1.21.1 分支**整块缺失** ⇒ neo 上箭塔忽略配置 (单塔覆盖与全局默认都无效, 用户实测"伤害不对") ✗
     * — 同款"重复实现必漂移" (错题 #348/#349)。现共享本函数 + 单测锁优先级 ✓。
     *
     * <p>优先级: **单塔覆盖** (非 NaN 且 > 0) &gt; **全局默认** (&gt; 0) &gt; {@code vanilla}
     * (1.20.1 传"2.0 + 力量等级×0.5 + 0.5"; 1.21.1 传 2.0 — 力量由原版命中时 modifyDamage 叠加) ✓。
     * 全局默认 2.0 = 原版箭基础伤害 ⇒ 未改配置者行为不变 ✓。
     */
    public static double effectiveArrowDamage(double override, double global, double vanilla) {
        if (!Double.isNaN(override) && override > 0) return override;
        if (global > 0) return global;
        return vanilla;
    }

    /** 伤害 clamp (0.5..100, GUI 滑块范围) */
    public static double clampDamage(double damage) {
        return Math.max(0.5, Math.min(100.0, damage));
    }

    /** 射击节奏 clamp (10..200 tick) */
    public static int clampFireInterval(int interval) {
        return Math.max(10, Math.min(200, interval));
    }

    /**
     * 目标判定 (纯函数): 必须是怪物 (MobCategory.MONSTER 或 Enemy 接口), 活体, 在半径平方内。
     * 排除不攻击类型 (盔甲架/潜影贝弹等 MISC)。与 TLM canAttack 语义对齐的轻量版。
     */
    public static boolean isHostileTarget(LivingEntity e) {
        if (e == null || !e.isAlive()) return false;
        if (e instanceof Mob mob) {
            return mob.getType().getCategory() == MobCategory.MONSTER;
        }
        return e instanceof Enemy;
    }

    /** 在半径平方内的最近怪物 (按距离升序取第一个); 无目标返回 null */
    public static LivingEntity nearestHostile(List<LivingEntity> candidates, Vec3 origin, double radiusSq) {
        if (candidates == null || origin == null) return null;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            if (!isHostileTarget(e)) continue;
            // 水平距离 (忽略 Y) — 半径=水平索敌范围; 塔放高处也能打地面怪 (3D 距离会因垂直差误过滤)
            double dx = e.getX() - origin.x;
            double dz = e.getZ() - origin.z;
            double d = dx * dx + dz * dz;
            if (d <= radiusSq && d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** 冷却判定 (纯函数, 时钟回绕守卫 — 模板同 ThrottleMath) */
    public static boolean isReady(long now, long lastFire, long interval) {
        return lastFire == 0 || now < lastFire || now - lastFire >= interval;
    }

    // ── 弹道纯函数 (v79.63 自 BE.fireArrow 抽出) ──

    /** 箭初速 (blocks/tick) — 原版弓满蓄 = 3.0 */
    public static final double ARROW_SPEED = 3.0;

    /** 箭重力 (blocks/tick²) — 原版 AbstractArrow 每 tick 减 0.05 */
    public static final double ARROW_GRAVITY = 0.05;

    /**
     * 弹道下坠补偿量 (格) — 飞行时间按 直线距离/初速 近似 (忽略 0.99 阻力)。
     *
     * <p>实测精度 (2026-09-11 模拟 + gametest): 8/16/24/32 格偏差 ≤ 0.17 格 (命中无忧);
     * 48 格偏 0.70 / 64 格偏 2.24 (超远距离需精确解 — 当前塔索敌半径上限 64, 已知边界)。
     */
    public static double ballisticDrop(double straightDist) {
        double flightT = straightDist / ARROW_SPEED;
        return 0.5 * ARROW_GRAVITY * flightT * flightT;
    }

    /**
     * 瞄准向量 = 目标眼位 + 下坠补偿 − 塔口 (纯函数, 单测覆盖)。
     * <p>瞄眼位是用户裁定; 补偿用 {@link #ballisticDrop} (按 3D 直线距离, 上下射同式)。
     */
    public static Vec3 aimAt(Vec3 origin, Vec3 eye) {
        double drop = ballisticDrop(eye.distanceTo(origin));
        return new Vec3(eye.x - origin.x, eye.y + drop - origin.y, eye.z - origin.z);
    }

    // ── 开火相位抖动 (v79.63, 错题 #277) ──

    /**
     * 原版受伤吸收窗口 (tick) — 目标 {@code invulnerableTime > 10} 期间, **伤害 ≤ lastHurt 的命中被整发丢弃**
     * (`LivingEntity:1101-1111` 源码实证: 命中后 `invulnerableTime` 置 20, 此前 10t 内 `amount <= lastHurt`
     * 直接 `return false`; `amount > lastHurt` 只吃差额 — 实测出现过 `dmg=3.936` 的过剩伤害分支)。
     *
     * <p>⇒ 多塔**同 tick 齐射**时箭同 tick 到达 → 第二发起被吞 (弹药/耐久白费)。本常量 = 相位量化的槽宽。
     */
    public static final int ABSORB_WINDOW = 10;

    /**
     * 开火相位抖动 (纯函数) — 按塔坐标派生**稳定**相位 (量化到 {@link #ABSORB_WINDOW} 的整数倍),
     * 让多塔落在不同相位 tick 上, 使箭的到达时刻错开 ≥ 吸收窗口, 避免齐射被整体吞掉。
     *
     * <p><b>为何量化而非连续随机</b>: 相位差恰 = 吸收窗口时, 每发都落在 {@code invulnerableTime <= 10}
     * 的窗口外 → 全额命中; 连续随机相位差 &lt; 10 时两塔仍互相吞。量化到 10t 槽位 = 稳定最优解
     * (period=20 时 2 槽 → 相邻槽恰好错开 10t)。
     *
     * <p><b>为何由坐标派生而非随机数</b>: 无状态、无需持久化 (卸载重载后相位不变), 同位置塔永远同相位
     * (行为可预期、可复现)。
     *
     * @param period 活动周期 (tick) — 传塔的 {@code TICK_INTERVAL}
     * @return 相位值 (0..period-1); period ≤ {@link #ABSORB_WINDOW} 时恒 0 (无法错开)
     */
    public static int phaseOf(int x, int y, int z, int period) {
        int slots = Math.max(1, period / ABSORB_WINDOW);
        if (slots <= 1) return 0;
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 15;
        return Math.floorMod(h, slots) * ABSORB_WINDOW;
    }

    /** 本 tick 是否轮到该塔活动 (相位闸门, 配合 {@link #phaseOf}; period ≤ 1 时恒真) */
    public static boolean isPhaseTick(long now, int phase, int period) {
        if (period <= 1) return true;
        return Math.floorMod(now - phase, period) == 0;
    }

    private DefenseTowerLogic() {}
}
