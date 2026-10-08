package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 工具参数 Codec 解析测试 (纯 JVM — Mojang Codec + Gson JSON, 禁 MC 类加载)。
 * 覆盖 compat/ai/tool 全部 14 个工具: round-trip (encode→decode 等价) +
 * 必填字段缺失报错 + 类型非法拒绝 (LLM 参数契约回归网)。
 * Codec 层纯 (仅 record + Codec/RecordCodecBuilder); MC 类型只出现在 onCall 方法体内, 测试不执行。
 */
class AiToolCodecTest {

    // ---------- 辅助: 统一解析/校验入口 (AAA Act) ----------

    private static <T> T decode(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    private static boolean parseFails(Codec<?> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent();
    }

    /** round-trip: parse → encode → parse, 两次结果等价 */
    private static <T> void assertRoundTrip(Codec<T> codec, String json) {
        T first = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
        var encoded = codec.encodeStart(JsonOps.INSTANCE, first).result().orElseThrow();
        var second = codec.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(first, second);
    }

    // ---------- move_to: x/y/z 必填 + mode 缺省 safe ----------

    @Test
    @DisplayName("MoveToTool Codec 解析 x/y/z, mode 缺省 safe")
    void moveTo_codec() {
        var result = decode(new MoveToTool().codec(), "{\"x\":10,\"y\":64,\"z\":-20}");
        assertEquals(10, result.x());
        assertEquals(64, result.y());
        assertEquals(-20, result.z());
        assertEquals("safe", result.mode());
    }

    @Test
    @DisplayName("MoveToTool Codec 往返等价")
    void moveTo_roundTrip() {
        assertRoundTrip(new MoveToTool().codec(), "{\"x\":10,\"y\":64,\"z\":-20,\"mode\":\"explorer\"}");
    }

    @Test
    @DisplayName("MoveToTool Codec 缺 z → 解析失败")
    void moveTo_missingRequired_fails() {
        assertTrue(parseFails(new MoveToTool().codec(), "{\"x\":1,\"y\":2}"));
    }

    @Test
    @DisplayName("MoveToTool Codec x 传字符串 → 解析失败")
    void moveTo_wrongType_fails() {
        assertTrue(parseFails(new MoveToTool().codec(), "{\"x\":\"a\",\"y\":1,\"z\":2}"));
    }

    // ---------- mine_block: x/y/z 必填 ----------

    @Test
    @DisplayName("MineBlockTool Codec 解析 x/y/z")
    void mineBlock_codec() {
        var result = decode(new MineBlockTool().codec(), "{\"x\":1,\"y\":2,\"z\":3}");
        assertEquals(1, result.x());
        assertEquals(2, result.y());
        assertEquals(3, result.z());
    }

    @Test
    @DisplayName("MineBlockTool Codec 往返等价")
    void mineBlock_roundTrip() {
        assertRoundTrip(new MineBlockTool().codec(), "{\"x\":1,\"y\":2,\"z\":3}");
    }

    @Test
    @DisplayName("MineBlockTool Codec 缺 z → 解析失败")
    void mineBlock_missingRequired_fails() {
        assertTrue(parseFails(new MineBlockTool().codec(), "{\"x\":1,\"y\":2}"));
    }

    @Test
    @DisplayName("MineBlockTool Codec y 传字符串 → 解析失败")
    void mineBlock_wrongType_fails() {
        assertTrue(parseFails(new MineBlockTool().codec(), "{\"x\":1,\"y\":\"b\",\"z\":3}"));
    }

    // ---------- collect_items: radius 可选默认 8 ----------

    @Test
    @DisplayName("CollectItemsTool Codec: 空参数 radius 默认 8")
    void collectItems_codec() {
        var result = decode(new CollectItemsTool().codec(), "{}");
        assertEquals(8, result.radius());
    }

    @Test
    @DisplayName("CollectItemsTool Codec 显式 radius")
    void collectItems_explicitRadius() {
        var result = decode(new CollectItemsTool().codec(), "{\"radius\":16}");
        assertEquals(16, result.radius());
    }

    @Test
    @DisplayName("CollectItemsTool Codec 往返等价")
    void collectItems_roundTrip() {
        assertRoundTrip(new CollectItemsTool().codec(), "{\"radius\":16}");
    }

    @Test
    @DisplayName("CollectItemsTool Codec radius 传字符串 → 回退默认值 8 (DFU optionalFieldOf 宽松语义)")
    void collectItems_wrongType_fallsBackToDefault() {
        var r = decode(new CollectItemsTool().codec(), "{\"radius\":\"big\"}");
        assertEquals(8, r.radius());
    }

    // ---------- interact_block: x/y/z 必填 ----------

    @Test
    @DisplayName("InteractBlockTool Codec 解析 x/y/z")
    void interactBlock_codec() {
        var result = decode(new InteractBlockTool().codec(), "{\"x\":-5,\"y\":64,\"z\":0}");
        assertEquals(-5, result.x());
        assertEquals(64, result.y());
        assertEquals(0, result.z());
    }

    @Test
    @DisplayName("InteractBlockTool Codec 往返等价")
    void interactBlock_roundTrip() {
        assertRoundTrip(new InteractBlockTool().codec(), "{\"x\":-5,\"y\":64,\"z\":0}");
    }

    @Test
    @DisplayName("InteractBlockTool Codec 缺 y → 解析失败")
    void interactBlock_missingRequired_fails() {
        assertTrue(parseFails(new InteractBlockTool().codec(), "{\"x\":1,\"z\":2}"));
    }

    @Test
    @DisplayName("InteractBlockTool Codec z 传字符串 → 解析失败")
    void interactBlock_wrongType_fails() {
        assertTrue(parseFails(new InteractBlockTool().codec(), "{\"x\":1,\"y\":2,\"z\":\"c\"}"));
    }

    // ---------- interact_entity: entity_id 必填 ----------

    @Test
    @DisplayName("InteractEntityTool Codec 解析 entity_id")
    void interactEntity_codec() {
        var result = decode(new InteractEntityTool().codec(), "{\"entity_id\":42}");
        assertEquals(42, result.entityId());
    }

    @Test
    @DisplayName("InteractEntityTool Codec 往返等价")
    void interactEntity_roundTrip() {
        assertRoundTrip(new InteractEntityTool().codec(), "{\"entity_id\":42}");
    }

    @Test
    @DisplayName("InteractEntityTool Codec 缺 entity_id → 解析失败")
    void interactEntity_missingRequired_fails() {
        assertTrue(parseFails(new InteractEntityTool().codec(), "{}"));
    }

    @Test
    @DisplayName("InteractEntityTool Codec entity_id 传字符串 → 解析失败")
    void interactEntity_wrongType_fails() {
        assertTrue(parseFails(new InteractEntityTool().codec(), "{\"entity_id\":\"x\"}"));
    }

    // ---------- melee_attack: entity_id 必填 ----------

    @Test
    @DisplayName("MeleeAttackTool Codec 解析 entity_id")
    void meleeAttack_codec() {
        var result = decode(new MeleeAttackTool().codec(), "{\"entity_id\":7}");
        assertEquals(7, result.entityId());
    }

    @Test
    @DisplayName("MeleeAttackTool Codec 往返等价")
    void meleeAttack_roundTrip() {
        assertRoundTrip(new MeleeAttackTool().codec(), "{\"entity_id\":7}");
    }

    @Test
    @DisplayName("MeleeAttackTool Codec 缺 entity_id → 解析失败")
    void meleeAttack_missingRequired_fails() {
        assertTrue(parseFails(new MeleeAttackTool().codec(), "{}"));
    }

    @Test
    @DisplayName("MeleeAttackTool Codec entity_id 传字符串 → 解析失败")
    void meleeAttack_wrongType_fails() {
        assertTrue(parseFails(new MeleeAttackTool().codec(), "{\"entity_id\":\"x\"}"));
    }

    // ---------- get_self_status: unit codec (无参) ----------

    @Test
    @DisplayName("GetSelfStatusTool UnitCodec: 空对象解析为空 Result")
    void getSelfStatus_unitDecode() {
        var result = decode(new GetSelfStatusTool().codec(), "{}");
        assertEquals(new GetSelfStatusTool.Result(), result);
    }

    @Test
    @DisplayName("GetSelfStatusTool UnitCodec: 忽略任意输入字段")
    void getSelfStatus_unitIgnoresFields() {
        var result = decode(new GetSelfStatusTool().codec(), "{\"anything\":123}");
        assertEquals(new GetSelfStatusTool.Result(), result);
    }

    @Test
    @DisplayName("GetSelfStatusTool UnitCodec 往返等价 (encode 产出空对象)")
    void getSelfStatus_roundTrip() {
        assertRoundTrip(new GetSelfStatusTool().codec(), "{}");
    }

    // ---------- switch_lma_task: task_id 必填 ----------

    @Test
    @DisplayName("SwitchLmaTaskTool Codec 解析 task_id")
    void switchLmaTask_codec() {
        var result = decode(new SwitchLmaTaskTool().codec(), "{\"task_id\":\"craft_chain\"}");
        assertEquals("craft_chain", result.taskId());
    }

    @Test
    @DisplayName("SwitchLmaTaskTool Codec 往返等价")
    void switchLmaTask_roundTrip() {
        assertRoundTrip(new SwitchLmaTaskTool().codec(), "{\"task_id\":\"craft_chain\"}");
    }

    @Test
    @DisplayName("SwitchLmaTaskTool Codec 缺 task_id → 解析失败")
    void switchLmaTask_missingRequired_fails() {
        assertTrue(parseFails(new SwitchLmaTaskTool().codec(), "{}"));
    }

    @Test
    @DisplayName("SwitchLmaTaskTool Codec task_id 传数字 → 解析失败")
    void switchLmaTask_wrongType_fails() {
        assertTrue(parseFails(new SwitchLmaTaskTool().codec(), "{\"task_id\":123}"));
    }

    // ---------- scan_blocks: block_id 必填 + radius 可选默认 8 ----------

    @Test
    @DisplayName("ScanBlocksTool Codec: 必填 block_id + 可选 radius 默认 8")
    void scanBlocks_codec() {
        var result = decode(new ScanBlocksTool().codec(), "{\"block_id\":\"minecraft:iron_ore\"}");
        assertEquals("minecraft:iron_ore", result.blockId());
        assertEquals(8, result.radius());
    }

    @Test
    @DisplayName("ScanBlocksTool Codec 往返等价")
    void scanBlocks_roundTrip() {
        assertRoundTrip(new ScanBlocksTool().codec(), "{\"block_id\":\"minecraft:iron_ore\",\"radius\":16}");
    }

    @Test
    @DisplayName("ScanBlocksTool Codec 缺 block_id → 解析失败")
    void scanBlocks_missingRequired_fails() {
        assertTrue(parseFails(new ScanBlocksTool().codec(), "{\"radius\":8}"));
    }

    @Test
    @DisplayName("ScanBlocksTool Codec block_id 传数字 → 解析失败")
    void scanBlocks_wrongType_fails() {
        assertTrue(parseFails(new ScanBlocksTool().codec(), "{\"block_id\":123}"));
    }

    // ---------- wait_ticks: ticks 可选默认 20 ----------

    @Test
    @DisplayName("WaitTicksTool Codec: 空参数 ticks 默认 20")
    void waitTicks_codec() {
        var result = decode(new WaitTicksTool().codec(), "{}");
        assertEquals(20, result.ticks());
    }

    @Test
    @DisplayName("WaitTicksTool Codec 显式 ticks")
    void waitTicks_explicit() {
        var result = decode(new WaitTicksTool().codec(), "{\"ticks\":100}");
        assertEquals(100, result.ticks());
    }

    @Test
    @DisplayName("WaitTicksTool Codec 往返等价")
    void waitTicks_roundTrip() {
        assertRoundTrip(new WaitTicksTool().codec(), "{\"ticks\":100}");
    }

    @Test
    @DisplayName("WaitTicksTool Codec ticks 传字符串 → 回退默认值 20 (DFU optionalFieldOf 宽松语义)")
    void waitTicks_wrongType_fails() {
        assertEquals(20, decode(new WaitTicksTool().codec(), "{\"ticks\":\"many\"}").ticks());
    }

    // ---------- look_around: radius 可选默认 8 ----------

    @Test
    @DisplayName("LookAroundTool Codec: 空参数 radius 默认 8")
    void lookAround_codec() {
        var result = decode(new LookAroundTool().codec(), "{}");
        assertEquals(8, result.radius());
    }

    @Test
    @DisplayName("LookAroundTool Codec 显式 radius")
    void lookAround_explicit() {
        var result = decode(new LookAroundTool().codec(), "{\"radius\":12}");
        assertEquals(12, result.radius());
    }

    @Test
    @DisplayName("LookAroundTool Codec 往返等价")
    void lookAround_roundTrip() {
        assertRoundTrip(new LookAroundTool().codec(), "{\"radius\":12}");
    }

    @Test
    @DisplayName("LookAroundTool Codec radius 传字符串 → 回退默认值 8 (DFU optionalFieldOf 宽松语义)")
    void lookAround_wrongType_fails() {
        assertEquals(8, decode(new LookAroundTool().codec(), "{\"radius\":\"near\"}").radius());
    }

    // ---------- scan_nearby_entities: radius 默认 32 + type_filter 默认 all ----------

    @Test
    @DisplayName("ScanNearbyEntitiesTool Codec: 空参数 radius 32 + type_filter all")
    void scanNearbyEntities_codec() {
        var result = decode(new ScanNearbyEntitiesTool().codec(), "{}");
        assertEquals(32, result.radius());
        assertEquals("all", result.typeFilter());
    }

    @Test
    @DisplayName("ScanNearbyEntitiesTool Codec 显式 radius + type_filter")
    void scanNearbyEntities_explicit() {
        var result = decode(new ScanNearbyEntitiesTool().codec(), "{\"radius\":10,\"type_filter\":\"hostile\"}");
        assertEquals(10, result.radius());
        assertEquals("hostile", result.typeFilter());
    }

    @Test
    @DisplayName("ScanNearbyEntitiesTool Codec 往返等价")
    void scanNearbyEntities_roundTrip() {
        assertRoundTrip(new ScanNearbyEntitiesTool().codec(), "{\"radius\":10,\"type_filter\":\"hostile\"}");
    }

    @Test
    @DisplayName("ScanNearbyEntitiesTool Codec radius 传字符串 → 回退默认值 32 (DFU optionalFieldOf 宽松语义)")
    void scanNearbyEntities_wrongType_fails() {
        assertEquals(32, decode(new ScanNearbyEntitiesTool().codec(), "{\"radius\":\"far\"}").radius());
    }

    // ---------- get_world_info: unit codec (无参) ----------

    @Test
    @DisplayName("GetWorldInfoTool UnitCodec: 空对象解析为空 Result")
    void getWorldInfo_unitDecode() {
        var result = decode(new GetWorldInfoTool().codec(), "{}");
        assertEquals(new GetWorldInfoTool.Result(), result);
    }

    @Test
    @DisplayName("GetWorldInfoTool UnitCodec 往返等价 (encode 产出空对象)")
    void getWorldInfo_roundTrip() {
        assertRoundTrip(new GetWorldInfoTool().codec(), "{}");
    }

    // ---------- read_blueprint: name 必填 ----------

    @Test
    @DisplayName("ReadBlueprintTool Codec 解析 name")
    void readBlueprint_codec() {
        var result = decode(new ReadBlueprintTool().codec(), "{\"name\":\"house\"}");
        assertEquals("house", result.name());
    }

    @Test
    @DisplayName("ReadBlueprintTool Codec 往返等价")
    void readBlueprint_roundTrip() {
        assertRoundTrip(new ReadBlueprintTool().codec(), "{\"name\":\"house\"}");
    }

    @Test
    @DisplayName("ReadBlueprintTool Codec 缺 name → 解析失败")
    void readBlueprint_missingRequired_fails() {
        assertTrue(parseFails(new ReadBlueprintTool().codec(), "{}"));
    }

    @Test
    @DisplayName("ReadBlueprintTool Codec name 传数字 → 解析失败")
    void readBlueprint_wrongType_fails() {
        assertTrue(parseFails(new ReadBlueprintTool().codec(), "{\"name\":42}"));
    }
}
