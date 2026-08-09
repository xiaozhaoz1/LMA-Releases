package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 工具参数 Codec 解析测试 (纯 JVM — Mojang Codec + Gson JSON)。
 */
class AiToolCodecTest {

    @Test
    @DisplayName("MoveToTool Codec 解析 x/y/z")
    void moveTo_codec() {
        var result = new MoveToTool().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"x\":10,\"y\":64,\"z\":-20}")).result().orElseThrow();
        assertEquals(10, result.x());
        assertEquals(64, result.y());
        assertEquals(-20, result.z());
    }

    @Test
    @DisplayName("ScanBlocksTool Codec: 必填 block_id + 可选 radius 默认 8")
    void scanBlocks_codec() {
        var result = new ScanBlocksTool().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"block_id\":\"minecraft:iron_ore\"}")).result().orElseThrow();
        assertEquals("minecraft:iron_ore", result.blockId());
        assertEquals(8, result.radius());
    }

    @Test
    @DisplayName("SwitchLmaTaskTool Codec 解析 task_id")
    void switchLmaTask_codec() {
        var result = new SwitchLmaTaskTool().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"task_id\":\"craft_chain\"}")).result().orElseThrow();
        assertEquals("craft_chain", result.taskId());
    }

    @Test
    @DisplayName("缺必填字段 → 解析失败")
    void missingRequired_fails() {
        assertTrue(new MoveToTool().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"x\":1,\"y\":2}")).error().isPresent());
    }

    @Test
    @DisplayName("WaitTicksTool Codec: 可选 ticks 默认 20")
    void waitTicks_codec() {
        var result = new WaitTicksTool().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{}")).result().orElseThrow();
        assertEquals(20, result.ticks());
    }
}
