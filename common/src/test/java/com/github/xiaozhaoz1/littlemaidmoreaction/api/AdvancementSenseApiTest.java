package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AdvancementSenseApi} 匹配逻辑单测 — 纯 JVM (回调不触碰 MC 参数, 错题 #174 铁律;
 * fire 的 level/maid 传 null 仅验证匹配, 不触发 MC 类型)。
 */
class AdvancementSenseApiTest {

    /** 记录 (advancementId, 触发序) — 校验顺序与叠加 */
    private static final class Rec {
        final String id;
        final String tag;
        Rec(String id, String tag) { this.id = id; this.tag = tag; }
    }

    private static List<Rec> run(String... ids) {
        List<Rec> out = new ArrayList<>();
        AdvancementSenseApi.fire(null, null, ids.length == 0 ? null : ids[0]);
        return out;
    }

    @Test
    void 通配反应_任意成就触发() {
        List<String> got = new ArrayList<>();
        AdvancementSenseApi.registerReaction((l, m, id) -> got.add(id));
        try {
            AdvancementSenseApi.fire(null, null, "minecraft:story/mine_stone");
            assertEquals(List.of("minecraft:story/mine_stone"), got);
        } finally {
            clearAll();
        }
    }

    @Test
    void 精确反应_仅指定成就触发() {
        List<String> got = new ArrayList<>();
        AdvancementSenseApi.registerReaction("minecraft:end/kill_dragon", (l, m, id) -> got.add(id));
        try {
            AdvancementSenseApi.fire(null, null, "minecraft:end/kill_dragon");
            AdvancementSenseApi.fire(null, null, "minecraft:story/mine_stone");
            assertEquals(List.of("minecraft:end/kill_dragon"), got);
        } finally {
            clearAll();
        }
    }

    @Test
    void 通配加指定_叠加触发() {
        List<String> got = new ArrayList<>();
        AdvancementSenseApi.registerReaction((l, m, id) -> got.add("wild:" + id));
        AdvancementSenseApi.registerReaction("minecraft:end/kill_dragon", (l, m, id) -> got.add("spec:" + id));
        try {
            AdvancementSenseApi.fire(null, null, "minecraft:end/kill_dragon");
            assertEquals(List.of("spec:minecraft:end/kill_dragon", "wild:minecraft:end/kill_dragon"), got);
        } finally {
            clearAll();
        }
    }

    @Test
    void 未注册反应_无副作用() {
        AdvancementSenseApi.fire(null, null, "minecraft:story/mine_stone");
        assertFalse(AdvancementSenseApi.hasAnyReaction());
    }

    @Test
    void null或空id_静默忽略() {
        List<String> got = new ArrayList<>();
        AdvancementSenseApi.registerReaction((l, m, id) -> got.add(id));
        try {
            AdvancementSenseApi.fire(null, null, null);
            AdvancementSenseApi.fire(null, null, "");
            assertEquals(List.of(), got);
        } finally {
            clearAll();
        }
    }

    @Test
    void 单个反应异常_不中断后续() {
        List<String> got = new ArrayList<>();
        AdvancementSenseApi.registerReaction((l, m, id) -> { throw new RuntimeException("boom"); });
        AdvancementSenseApi.registerReaction((l, m, id) -> got.add(id));
        try {
            AdvancementSenseApi.fire(null, null, "minecraft:story/mine_stone");
            assertEquals(List.of("minecraft:story/mine_stone"), got);
        } finally {
            clearAll();
        }
    }

    @Test
    void 重复注册同id_按注册顺序执行() {
        List<String> got = new ArrayList<>();
        AdvancementSenseApi.registerReaction("x", (l, m, id) -> got.add("1"));
        AdvancementSenseApi.registerReaction("x", (l, m, id) -> got.add("2"));
        try {
            AdvancementSenseApi.fire(null, null, "x");
            assertEquals(List.of("1", "2"), got);
        } finally {
            clearAll();
        }
    }

    @Test
    void 注册空参数_抛IllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> AdvancementSenseApi.registerReaction((AdvancementSenseApi.AdvancementReaction) null));
        assertThrows(IllegalArgumentException.class, () -> AdvancementSenseApi.registerReaction("", (l, m, id) -> {}));
        assertThrows(IllegalArgumentException.class, () -> AdvancementSenseApi.registerReaction(null, (l, m, id) -> {}));
        clearAll();
    }

    /** 清空注册表 — 单测隔离 (反射清静态字段) */
    private static void clearAll() {
        try {
            var w = AdvancementSenseApi.class.getDeclaredField("WILDCARD_REACTIONS");
            w.setAccessible(true);
            ((List<?>) w.get(null)).clear();
            var s = AdvancementSenseApi.class.getDeclaredField("SPECIFIC_REACTIONS");
            s.setAccessible(true);
            ((java.util.Map<?, ?>) s.get(null)).clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
