package littlemaidmoreaction.littlemaidmoreaction.compat.ai.scanner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * NearbyBlockScanner 纯逻辑测试 — classify() 和 toDisplayName() 不依赖 Minecraft。
 */
class NearbyBlockScannerTest {

    @Test
    void classify_altar() {
        assertEquals("altar", NearbyBlockScanner.classify("touhou_little_maid:altar"));
        assertEquals("altar", NearbyBlockScanner.classify("bloodmagic:blood_altar"));
    }

    @Test
    void classify_storage() {
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:chest"));
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:trapped_chest"));
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:barrel"));
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:shulker_box"));
        assertEquals("storage", NearbyBlockScanner.classify("minecraft:ender_chest"));
    }

    @Test
    void classify_crafting() {
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:crafting_table"));
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:furnace"));
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:blast_furnace"));
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:smoker"));
        assertEquals("crafting", NearbyBlockScanner.classify("minecraft:brewing_stand"));
    }

    @Test
    void classify_enchanting() {
        assertEquals("enchanting", NearbyBlockScanner.classify("minecraft:enchanting_table"));
        assertEquals("enchanting", NearbyBlockScanner.classify("minecraft:anvil"));
        assertEquals("enchanting", NearbyBlockScanner.classify("minecraft:grindstone"));
        assertEquals("enchanting", NearbyBlockScanner.classify("minecraft:smithing_table"));
    }

    @Test
    void classify_redstone() {
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:dispenser"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:dropper"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:hopper"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:observer"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:piston"));
        assertEquals("redstone", NearbyBlockScanner.classify("minecraft:sticky_piston"));
    }

    @Test
    void classify_utility() {
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:beacon"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:conduit"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:cauldron"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:composter"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:loom"));
        assertEquals("utility", NearbyBlockScanner.classify("minecraft:stonecutter"));
    }

    @Test
    void classify_unknown() {
        assertEquals("other", NearbyBlockScanner.classify("minecraft:dirt"));
        assertEquals("other", NearbyBlockScanner.classify("minecraft:stone"));
        assertEquals("other", NearbyBlockScanner.classify("somemod:random_block"));
    }

    @Test
    void classify_caseInsensitive() {
        assertEquals("altar", NearbyBlockScanner.classify("Minecraft:ALTAR"));
        assertEquals("storage", NearbyBlockScanner.classify("MOD:Chest"));
        assertEquals("crafting", NearbyBlockScanner.classify("Minecraft:FURNACE"));
    }

    // ── 床 ──

    @Test
    void classify_bed() {
        assertEquals("bed", NearbyBlockScanner.classify("minecraft:white_bed"));
        assertEquals("bed", NearbyBlockScanner.classify("minecraft:red_bed"));
        assertEquals("bed", NearbyBlockScanner.classify("touhou_little_maid:maid_bed"));
    }

    // ── TLM 方块精确分类 ──

    @Test
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
    void classify_tlm_fallback() {
        assertEquals("tlm", NearbyBlockScanner.classify("touhou_little_maid:keyboard"));
        assertEquals("tlm", NearbyBlockScanner.classify("touhou_little_maid:gomoku"));
        assertEquals("tlm", NearbyBlockScanner.classify("touhou_little_maid:cchess"));
    }

    @Test
    void toDisplayName() {
        // toDisplayName("crafting_table") → "crafting table"
        var group = new NearbyBlockScanner.BlockGroup("crafting", "minecraft:crafting_table", "crafting table", 1, 5.0, 5.0);
        assertTrue(group.toContextLine().contains("crafting table"));
        assertTrue(group.toContextLine().contains("minecraft:crafting_table"));
        assertTrue(group.toContextLine().contains("5.0 blocks"));
    }

    @Test
    void scanResult_empty() {
        var result = new NearbyBlockScanner.ScanResult(0, 16, java.util.List.of());
        assertTrue(result.toContextString().contains("No functional blocks found"));
    }

    @Test
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
