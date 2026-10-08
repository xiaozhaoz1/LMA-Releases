package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ItemFilters} 黑白名单判定测试 — 纯字符串逻辑部分
 * (ItemStack/BlockState/Item 重载依赖 MC 注册表, 由双编译 + gametest 覆盖)。
 *
 * <p>错题 #191 回归: 匹配语义 — 黑名单命中拒绝; 白名单非空未命中拒绝; 皆空放行;
 * modid:* 通配整 mod; 名单匹配必须用注册表完整 id (不可用 Item.toString())。</p>
 */
public class ItemFiltersTest {

    // ── isAllowed(String, black, white) ──

    @Test
    @DisplayName("黑白名单皆空 → 全部放行")
    void emptyLists_allowAll() {
        assertTrue(ItemFilters.isAllowed("minecraft:raw_iron", List.of(), List.of()));
    }

    @Test
    @DisplayName("黑名单精确命中 → 拒绝")
    void blacklistExact_rejects() {
        assertFalse(ItemFilters.isAllowed("minecraft:raw_iron", List.of("minecraft:raw_iron"), List.of()));
    }

    @Test
    @DisplayName("黑名单未命中 → 放行")
    void blacklistMiss_allow() {
        assertTrue(ItemFilters.isAllowed("minecraft:raw_copper", List.of("minecraft:raw_iron"), List.of()));
    }

    @Test
    @DisplayName("白名单精确命中 → 放行")
    void whitelistExact_allow() {
        assertTrue(ItemFilters.isAllowed("minecraft:raw_iron", List.of(), List.of("minecraft:raw_iron")));
    }

    @Test
    @DisplayName("白名单非空未命中 → 拒绝 (白名单=允许集合)")
    void whitelistNonEmptyMiss_rejects() {
        assertFalse(ItemFilters.isAllowed("minecraft:raw_copper", List.of(), List.of("minecraft:raw_iron")));
    }

    @Test
    @DisplayName("modid:* 通配整 mod → 放行")
    void wildcard_allowAllOfMod() {
        assertTrue(ItemFilters.isAllowed("create:rose_quartz", List.of(), List.of("create:*")));
    }

    @Test
    @DisplayName("通配不匹配其它 mod → 拒绝")
    void wildcard_otherMod_rejects() {
        assertFalse(ItemFilters.isAllowed("minecraft:raw_iron", List.of(), List.of("create:*")));
    }

    @Test
    @DisplayName("黑名单优先于白名单")
    void blacklistWins() {
        assertFalse(ItemFilters.isAllowed("minecraft:raw_iron",
                List.of("minecraft:raw_iron"), List.of("minecraft:raw_iron")));
    }

    @Test
    @DisplayName("条目 trim 前后空白 → 仍匹配")
    void trimmedEntries_match() {
        assertTrue(ItemFilters.isAllowed("minecraft:raw_iron", List.of(), List.of("  minecraft:raw_iron  ")));
    }

    @Test
    @DisplayName("空条目 (空白/逗号残留) 跳过不误杀")
    void emptyEntries_skipped() {
        assertTrue(ItemFilters.isAllowed("minecraft:raw_iron", List.of(" ", ","), List.of()));
    }

    // ── effective (per-maid 覆盖全局) ──

    @Test
    @DisplayName("per-maid 非空覆盖全局")
    void maidList_overridesGlobal() {
        List<String> result = ItemFilters.effective(List.of("minecraft:raw_iron"), List.of("minecraft:dirt"));
        assertEquals(List.of("minecraft:raw_iron"), result);
    }

    @Test
    @DisplayName("per-maid 空 → 用全局")
    void maidListEmpty_usesGlobal() {
        List<String> result = ItemFilters.effective(List.of(), List.of("minecraft:dirt"));
        assertEquals(List.of("minecraft:dirt"), result);
    }

    @Test
    @DisplayName("per-maid null → 用全局")
    void maidListNull_usesGlobal() {
        List<String> result = ItemFilters.effective(null, List.of("minecraft:dirt"));
        assertEquals(List.of("minecraft:dirt"), result);
    }

    @Test
    @DisplayName("两者皆空 → 空名单 (全部放行语义)")
    void bothEmpty_returnsEmpty() {
        assertTrue(ItemFilters.effective(null, null).isEmpty());
    }

    // ── effectivePair (v79.61x 重复抽取) ──

    @Test
    @DisplayName("effectivePair: 空配置 → 用全局, [0]=black [1]=white")
    void effectivePair_emptyConfig_usesGlobal() {
        var pair = ItemFilters.effectivePair(new net.minecraft.nbt.CompoundTag(),
                List.of("minecraft:dirt"), List.of("minecraft:stone"));
        assertEquals(2, pair.size());
        assertEquals(List.of("minecraft:dirt"), pair.get(0));
        assertEquals(List.of("minecraft:stone"), pair.get(1));
    }

    @Test
    @DisplayName("effectivePair: per-maid 名单非空覆盖全局")
    void effectivePair_maidOverridesGlobal() {
        net.minecraft.nbt.CompoundTag cfg = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag black = new net.minecraft.nbt.ListTag();
        black.add(net.minecraft.nbt.StringTag.valueOf("minecraft:raw_iron"));
        cfg.put(ItemFilters.KEY_BLACKLIST, black);
        var pair = ItemFilters.effectivePair(cfg,
                List.of("minecraft:dirt"), List.of("minecraft:stone"));
        assertEquals(List.of("minecraft:raw_iron"), pair.get(0)); // black 覆盖
        assertEquals(List.of("minecraft:stone"), pair.get(1));    // white 仍全局
    }
}
