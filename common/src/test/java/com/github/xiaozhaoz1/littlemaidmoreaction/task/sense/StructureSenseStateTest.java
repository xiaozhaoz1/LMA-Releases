package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StructureSense 状态机纯 JVM 单测 (v79.61 用户裁定重设计) —
 * phaseOf/structStep(bubble 语义)/kindOf/StructConfig 纯函数全覆盖。
 * 不触 PassiveTaskConfig (MC config 类, 错题 #174 铁律) — 配置值参数注入。
 *
 * <p>语义: 发现 (NEAR 首扫/转正 discover 气泡) / 进入 (enter 气泡+聊天 "到X啦" — v79.6x 用户裁定) /
 * 刷新 (NEAR 停留每 refreshTicks, 上限 refreshMax) / leave 静默信号 (LLM 预留)。
 * bubble = 最近结构: 只冻结 NEAR 停留预算, 相位迁移不受影响 (displaced 结构 enter/leave 照发)。
 *
 * <p>v79.61 审查修复: displaced NEAR 转正 (首次成为 bubble) 首信号 DISCOVER 而非 REFRESH; IN 进入即提示, 停留恒静默 (v79.6x 用户裁定);
 * 同轮 ≥2 结构 discover 合并为一条气泡 (joinMergedText 纯函数)。
 */
class StructureSenseStateTest {

    private static final long T0 = 10_000L;
    private static final StructureSense.StructConfig CFG =
            StructureSense.StructConfig.of(40, 100, 2400, 3);

    // ── phaseOf ──

    @Test
    void 相位_enter边界内_IN() {
        assertEquals(StructureSense.Phase.IN, StructureSense.phaseOf(39.0 * 39.0, CFG.enterSqr(), CFG.leaveSqr()));
        assertEquals(StructureSense.Phase.IN, StructureSense.phaseOf(40.0 * 40.0, CFG.enterSqr(), CFG.leaveSqr()));  // 含边界
    }

    @Test
    void 相位_enter与leave之间_NEAR() {
        assertEquals(StructureSense.Phase.NEAR, StructureSense.phaseOf(41.0 * 41.0, CFG.enterSqr(), CFG.leaveSqr()));
        assertEquals(StructureSense.Phase.NEAR, StructureSense.phaseOf(100.0 * 100.0, CFG.enterSqr(), CFG.leaveSqr()));  // 含边界
    }

    @Test
    void 相位_leave外_OUT() {
        assertEquals(StructureSense.Phase.OUT, StructureSense.phaseOf(101.0 * 101.0, CFG.enterSqr(), CFG.leaveSqr()));
        assertEquals(StructureSense.Phase.IN, StructureSense.phaseOf(0, CFG.enterSqr(), CFG.leaveSqr()));
    }

    // ── structStep: UNKNOWN ──

    @Test
    void 状态机_首次发现NEAR且最近_discover() {
        var step = StructureSense.structStep(null, StructureSense.Phase.NEAR, T0, true, CFG);
        assertEquals(new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 1), step.next());
        assertEquals(Set.of(StructureSense.SignalKind.DISCOVER), step.signals());
    }

    @Test
    void 状态机_首次发现NEAR非最近_静默() {
        var step = StructureSense.structStep(null, StructureSense.Phase.NEAR, T0, false, CFG);
        assertEquals(new StructureSense.StructState(StructureSense.Phase.NEAR, 0L, 0), step.next());
        assertTrue(step.signals().isEmpty());
    }

    @Test
    void 状态机_首次发现IN且最近_仅enter() {
        var step = StructureSense.structStep(null, StructureSense.Phase.IN, T0, true, CFG);
        assertEquals(new StructureSense.StructState(StructureSense.Phase.IN, T0, 1), step.next());
        assertEquals(Set.of(StructureSense.SignalKind.ENTER), step.signals());
    }

    @Test
    void 状态机_首次发现IN非最近_仅enter() {
        var step = StructureSense.structStep(null, StructureSense.Phase.IN, T0, false, CFG);
        assertEquals(new StructureSense.StructState(StructureSense.Phase.IN, 0L, 0), step.next());
        assertEquals(Set.of(StructureSense.SignalKind.ENTER), step.signals());
    }

    @Test
    void 状态机_首次发现OUT_静默剪枝() {
        var step = StructureSense.structStep(null, StructureSense.Phase.OUT, T0, true, CFG);
        assertNull(step.next());
        assertTrue(step.signals().isEmpty());
    }

    // ── structStep: NEAR 停留预算 ──

    @Test
    void 状态机_NEAR停留未到刷新点_静默() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 1);
        var step = StructureSense.structStep(prev, StructureSense.Phase.NEAR, T0 + 1000, true, CFG);
        assertEquals(prev, step.next());
        assertTrue(step.signals().isEmpty());
    }

    @Test
    void 状态机_NEAR停留到点且未满_refresh() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 1);
        var step = StructureSense.structStep(prev, StructureSense.Phase.NEAR, T0 + 2400, true, CFG);
        assertEquals(new StructureSense.StructState(StructureSense.Phase.NEAR, T0 + 2400, 2), step.next());
        assertEquals(Set.of(StructureSense.SignalKind.REFRESH), step.signals());
    }

    @Test
    void 状态机_NEAR停留已满上限_静默且节拍不漂移() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 3);
        var step = StructureSense.structStep(prev, StructureSense.Phase.NEAR, T0 + 2400, true, CFG);
        assertEquals(prev, step.next());
        assertTrue(step.signals().isEmpty());
    }

    @Test
    void 状态机_displaced到点_冻结预算不消耗() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 1);
        var step = StructureSense.structStep(prev, StructureSense.Phase.NEAR, T0 + 2400, false, CFG);
        assertEquals(prev, step.next());  // count/lastRemind 原样 — 走回后预算完好
        assertTrue(step.signals().isEmpty());
    }

    @Test
    void 状态机_displaced未到点_静默保留() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 2);
        var step = StructureSense.structStep(prev, StructureSense.Phase.NEAR, T0 + 500, false, CFG);
        assertEquals(prev, step.next());
        assertTrue(step.signals().isEmpty());
    }

    // ── structStep: 迁移 ──

    @Test
    void 状态机_NEAR进IN_enter且预算保留() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 2);
        var step = StructureSense.structStep(prev, StructureSense.Phase.IN, T0 + 100, true, CFG);
        assertEquals(new StructureSense.StructState(StructureSense.Phase.IN, T0, 2), step.next());
        assertEquals(Set.of(StructureSense.SignalKind.ENTER), step.signals());
    }

    @Test
    void 状态机_displaced进IN_enter照发() {
        // 非活跃结构走进 40 格 — enter 不因 bubble=false 丢失 (LLM 上下文完整)
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 1);
        var step = StructureSense.structStep(prev, StructureSense.Phase.IN, T0 + 100, false, CFG);
        assertEquals(Set.of(StructureSense.SignalKind.ENTER), step.signals());
    }

    @Test
    void 状态机_IN停留_静默() {
        var prev = new StructureSense.StructState(StructureSense.Phase.IN, T0, 1);
        var step = StructureSense.structStep(prev, StructureSense.Phase.IN, T0 + 5000, true, CFG);
        assertEquals(prev, step.next());
        assertTrue(step.signals().isEmpty());
    }

    @Test
    void 状态机_IN出enter未出leave_静默重置预算() {
        var prev = new StructureSense.StructState(StructureSense.Phase.IN, T0, 1);
        var step = StructureSense.structStep(prev, StructureSense.Phase.NEAR, T0 + 500, true, CFG);
        assertEquals(new StructureSense.StructState(StructureSense.Phase.NEAR, T0 + 500, 0), step.next());
        assertTrue(step.signals().isEmpty());
    }

    @Test
    void 状态机_NEAR出leave_leave剪枝() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 2);
        var step = StructureSense.structStep(prev, StructureSense.Phase.OUT, T0 + 100, true, CFG);
        assertNull(step.next());
        assertEquals(Set.of(StructureSense.SignalKind.LEAVE), step.signals());
    }

    @Test
    void 状态机_IN出leave_leave剪枝() {
        var prev = new StructureSense.StructState(StructureSense.Phase.IN, T0, 1);
        var step = StructureSense.structStep(prev, StructureSense.Phase.OUT, T0 + 100, true, CFG);
        assertNull(step.next());
        assertEquals(Set.of(StructureSense.SignalKind.LEAVE), step.signals());
    }

    // ── structStep: displaced 转正 (v79.61 审查修复 MED-1) ──

    @Test
    void 状态机_displacedNEAR转正_首信号discover非refresh() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, 0L, 0);   // 从未气泡的 displaced
        var step = StructureSense.structStep(prev, StructureSense.Phase.NEAR, T0, true, CFG);
        assertEquals(new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 1), step.next());
        assertEquals(Set.of(StructureSense.SignalKind.DISCOVER), step.signals());
    }

    @Test
    void 状态机_displacedIN停留_静默() {
        var prev = new StructureSense.StructState(StructureSense.Phase.IN, 0L, 0);
        var step = StructureSense.structStep(prev, StructureSense.Phase.IN, T0, true, CFG);
        assertEquals(prev, step.next());
        assertTrue(step.signals().isEmpty());
    }

    @Test
    void 状态机_预算重置后不重复discover() {
        var prev = new StructureSense.StructState(StructureSense.Phase.NEAR, T0, 0);   // IN→NEAR 预算重置 (lastRemind=now)
        var step = StructureSense.structStep(prev, StructureSense.Phase.NEAR, T0 + 100, true, CFG);
        assertEquals(prev, step.next());
        assertTrue(step.signals().isEmpty());
    }

    // ── enter 模板 (v79.6x 用户裁定: 进入结构气泡+聊天) ──

    @Test
    void enter模板_村庄族走专属键() {
        assertEquals(StructureSense.TIP_ENTER_VILLAGE, StructureSense.enterTipKey("structure_sense.structure.village"));
        assertEquals(StructureSense.TIP_ENTER_VILLAGE, StructureSense.enterTipKey("structure_sense.structure.village_taiga"));
    }

    @Test
    void enter模板_非村庄走通用池() {
        String key = StructureSense.enterTipKey("structure_sense.structure.mineshaft");
        assertTrue(StructureSense.TIP_ENTER_KEYS.contains(key));
    }

    // ── 合并文案 (v79.6x Component 化, 纯函数) ──

    private static net.minecraft.network.chat.Component tip(String structKey) {
        return StructureSense.tipComponent("structure_sense.tip.near.0", null, structKey);
    }

    @Test
    void 合并组件_单句即自身() {
        var c = tip("structure_sense.structure.village");
        assertEquals(c, StructureSense.joinComponents(List.of(c)));
    }

    @Test
    void 合并组件_两句顿号连接() {
        var joined = StructureSense.joinComponents(List.of(
                tip("structure_sense.structure.village"),
                tip("structure_sense.structure.shipwreck")));
        assertNotNull(joined);
        assertTrue(joined instanceof net.minecraft.network.chat.MutableComponent m
                && m.getSiblings().size() == 2);   // [、, 第二句]
    }

    @Test
    void 合并组件_空表null() {
        assertNull(StructureSense.joinComponents(List.of()));
    }

    // ── kindOf ──

    @Test
    void 信号后缀_四类正解() {
        assertEquals(StructureSense.SignalKind.DISCOVER, StructureSense.kindOf("discover"));
        assertEquals(StructureSense.SignalKind.REFRESH, StructureSense.kindOf("refresh"));
        assertEquals(StructureSense.SignalKind.ENTER, StructureSense.kindOf("enter"));
        assertEquals(StructureSense.SignalKind.LEAVE, StructureSense.kindOf("leave"));
        assertNull(StructureSense.kindOf("nearby"));  // v79.60 旧后缀
        assertNull(StructureSense.kindOf(""));
        assertNull(StructureSense.kindOf("DISCOVER"));  // 大小写敏感
    }

    // ── StructConfig ──

    @Test
    void 配置_enter不小于leave时钳制() {
        var cfg = StructureSense.StructConfig.of(100, 40, 2400, 3);
        assertEquals(39, cfg.enterDist());   // min(100, 40-1)
        assertEquals(40, cfg.leaveDist());
        assertEquals(39 * 39, cfg.enterSqr());
        assertEquals(40 * 40, cfg.leaveSqr());
    }

    @Test
    void 配置_正常值平方正确() {
        assertEquals(1600, CFG.enterSqr());
        assertEquals(10000, CFG.leaveSqr());
        assertEquals(2400, CFG.refreshTicks());
        assertEquals(3, CFG.refreshMax());
    }
}
