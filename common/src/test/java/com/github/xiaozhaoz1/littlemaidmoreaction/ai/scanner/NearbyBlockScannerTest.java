package com.github.xiaozhaoz1.littlemaidmoreaction.ai.scanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * NearbyBlockScanner 纯逻辑测试 — classify() 和 toDisplayName() 不依赖 Minecraft。
 */
class NearbyBlockScannerTest {

    @Test
    @DisplayName("分类: 祭坛 (TLM 祭坛/血魔祭坛)")
    void classify_altar() {
        assertEquals("altar", NearbyBlockScanner.classify("touhou_little_maid:altar"));
        assertEquals("altar", NearbyBlockScanner.classify("bloodmagic:blood_altar"));
    }

    @Test
    @DisplayName("分类: 存储容器 (箱子/桶/潜影盒/末影箱)")
    void classify_storage() {
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:chest"));
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:trapped_chest"));
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:barrel"));
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:shulker_box"));
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:ender_chest"));
    }

    @Test
    @DisplayName("分类: 合成/熔炉工作台 (工作台/熔炉/烟熏炉/酿造台)")
    void classify_crafting() {
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:crafting_table"));
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:furnace"));
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:blast_furnace"));
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:smoker"));
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:brewing_stand"));
    }

    @Test
    @DisplayName("分类: 附魔/铁砧设备")
    void classify_enchanting() {
        assertEquals("enchanting", NearbyBlockScanner.classify("minecraft:enchanting_table"));
        assertEquals("enchanting", NearbyBlockScanner.classify("minecraft:anvil"));
        assertEquals("enchanting", NearbyBlockScanner.classify("minecraft:grindstone"));
        assertEquals("enchanting", NearbyBlockScanner.classify("minecraft:smithing_table"));
    }

    @Test
    @DisplayName("分类: 红石设备 (发射器/漏斗/观察者/活塞)")
    void classify_redstone() {
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:dispenser"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:dropper"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:hopper"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:observer"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:piston"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:sticky_piston"));
    }

    @Test
    @DisplayName("分类: 实用方块 (信标/炼药锅/切石机等)")
    void classify_utility() {
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:beacon"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:conduit"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:cauldron"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:composter"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:loom"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:stonecutter"));
    }

    @Test
    @DisplayName("分类: 未知方块回退 other")
    void classify_unknown() {
        assertEquals("other", NearbyBlockScanner.classify("minecraft:dirt"));
        assertEquals("other", NearbyBlockScanner.classify("minecraft:stone"));
        assertEquals("other", NearbyBlockScanner.classify("somemod:random_block"));
    }

    @Test
    @DisplayName("分类: 大小写不敏感 (Minecraft:ALTAR → altar)")
    void classify_caseInsensitive() {
        assertEquals("altar", NearbyBlockScanner.classify("Minecraft:ALTAR"));
        assertEquals("storage", NearbyBlockScanner.classify("MOD:Chest"));
        assertEquals("crafting", NearbyBlockScanner.classify("Minecraft:FURNACE"));
    }

    // ── 床 ──

    @Test
    @DisplayName("分类: 床 (含 TLM 女仆床)")
    void classify_bed() {
        assertEquals("bed", NearbyBlockScanner.classify("minecraft:white_bed"));
        assertEquals("bed", NearbyBlockScanner.classify("minecraft:red_bed"));
        assertEquals("bed", NearbyBlockScanner.classify("touhou_little_maid:maid_bed"));
    }

    // ── TLM 方块精确分类 ──

    @Test
    @DisplayName("分类: TLM 方块精确分类 (模型/信标/神社/电脑等)")
    void classify_tlm_blocks() {
        assertEquals("model", NearbyBlockScanner.classify("touhou_little_maid:garage_kit"));
        assertEquals("model", NearbyBlockScanner.classify("touhou_little_maid:statue"));
        assertEquals("model", NearbyBlockScanner.classify("touhou_little_maid:model_switcher"));
        assertEquals("beacon", NearbyBlockScanner.classify("touhou_little_maid:maid_beacon"));
        assertEquals("rest", NearbyBlockScanner.classify("touhou_little_maid:picnic_mat"));
        assertEquals("shrine", NearbyBlockScanner.classify("touhou_little_maid:shrine"));
        assertEquals("trade", NearbyBlockScanner.classify("touhou_little_maid:computer"));
        assertEquals("enchanting", NearbyBlockScanner.classify("touhou_little_maid:bookshelf"));
        assertEquals("storage", NearbyBlockScanner.classify("touhou_little_maid:snack_cabinet"));
        assertEquals("protection", NearbyBlockScanner.classify("touhou_little_maid:scarecrow"));
    }

    @Test
    @DisplayName("分类: TLM 未细分方块回退 tlm")
    void classify_tlm_fallback() {
        assertEquals("tlm", NearbyBlockScanner.classify("touhou_little_maid:keyboard"));
        assertEquals("tlm", NearbyBlockScanner.classify("touhou_little_maid:gomoku"));
        assertEquals("tlm", NearbyBlockScanner.classify("touhou_little_maid:cchess"));
    }

    @Test
    @DisplayName("BlockGroup 上下文行含显示名/方块 ID/距离")
    void toDisplayName() {
        // toDisplayName("crafting_table") → "crafting table"
        var group = new NearbyBlockScanner.BlockGroup("crafting", "minecraft:crafting_table", "crafting table", 1, 5.0, 5.0);
        assertTrue(group.toContextLine().contains("crafting table"));
        assertTrue(group.toContextLine().contains("minecraft:crafting_table"));
        assertTrue(group.toContextLine().contains("5.0 blocks"));
    }

    @Test
    @DisplayName("空扫描结果: 上下文串含 'No functional blocks found'")
    void scanResult_empty() {
        var result = new NearbyBlockScanner.ScanResult(0, 16, java.util.List.of());
        assertTrue(result.toContextString().contains("No functional blocks found"));
    }

    @Test
    @DisplayName("多组扫描结果: 上下文串含分组名与距离")
    void scanResult_withBlocks() {
        var groups = java.util.List.of(
            new NearbyBlockScanner.BlockGroup("altar", "tl:altar", "altar", 1, 8.3, 8.3),
            new NearbyBlockScanner.BlockGroup("storage", "mc:chest", "chest", 2, 5.1, 12.0)
        );
        var result = new NearbyBlockScanner.ScanResult(3, 16, groups);
        String s = result.toContextString();
        assertTrue(s.contains("[altar]"));
        assertTrue(s.contains("[storage]"));
        assertTrue(s.contains("8.3 blocks"));
        assertTrue(s.contains("5.1 blocks"));
    }
}
