package com.github.xiaozhaoz1.littlemaidmoreaction.resource;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LmaAnimationDef} 测试 (审计 T1 — core 归位后 resource 域漏网点补上):
 * fallback 默认 / with* 不可变更新 / Gson 字段名 round-trip (落盘兼容性)。
 */
public class LmaAnimationDefTest {

    @Test
    @DisplayName("fallback 默认值契约")
    void fallback_defaults() {
        LmaAnimationDef def = LmaAnimationDef.fallback("anim");
        assertEquals("anim", def.name());
        assertEquals(LmaAnimationDef.DEFAULT_PRIORITY, def.priority());
        assertTrue(def.lockMovement());
        assertTrue(def.freezeAI());
        assertFalse(def.interruptible());
        assertTrue(def.extra().isEmpty());
    }

    @Test
    @DisplayName("with* 不可变更新 (其余字段保留)")
    void withMethods_immutable() {
        LmaAnimationDef def = LmaAnimationDef.fallback("a");
        LmaAnimationDef renamed = def.withName("b");
        assertEquals("b", renamed.name());
        assertEquals(def.priority(), renamed.priority());
        assertEquals(250, def.withPriority(250).priority());
        assertFalse(def.withLockMove(false).lockMovement());
        assertFalse(def.withFreezeAI(false).freezeAI());
        assertTrue(def.withInterruptible(true).interruptible());
    }

    @Test
    @DisplayName("Gson round-trip 按字段名 (旧 animationsetup/*.json 兼容)")
    void gson_roundTrip() {
        LmaAnimationDef def = new LmaAnimationDef("combat/slash", 120, true, false, true, Map.of("k", "v"));
        String json = new Gson().toJson(def);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"priority\""));
        LmaAnimationDef back = new Gson().fromJson(json, LmaAnimationDef.class);
        assertEquals(def, back);
    }
}
