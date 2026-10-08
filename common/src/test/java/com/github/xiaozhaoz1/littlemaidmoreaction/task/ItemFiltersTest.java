package com.github.xiaozhaoz1.littlemaidmoreaction.task;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 黑白名单**语义**单测 (纯 JVM — 只用 id 级 API, 不碰注册表)。
 *
 * <p>用户实机问"熔炉这种设置很多, 黑白名单能不能用" ⇒ 语义层必须被测试锁住:
 * 优先级 / 白名单=允许集合 / 空=全放行 / {@code modid:*} 通配 / per-maid 覆盖全局 / NBT 读写。
 * (注册表级 API ({@code isAllowed(Item/ItemStack/BlockState)}) 由 gametest 端到端覆盖: {@code lmaFurnaceBlacklist}。)
 */
class ItemFiltersTest {

    private static final List<String> NONE = List.of();

    @Test
    void emptyListsAllowEverything() {
        assertTrue(ItemFilters.isAllowed("minecraft:iron_ore", NONE, NONE));
        assertTrue(ItemFilters.isAllowed("create:copper_ore", null, null));
    }

    @Test
    void blacklistAlwaysWins() {
        List<String> black = List.of("minecraft:iron_ore");
        assertFalse(ItemFilters.isAllowed("minecraft:iron_ore", black, NONE));
        assertTrue(ItemFilters.isAllowed("minecraft:gold_ore", black, NONE));
        // 同时在黑白名单 ⇒ 黑名单优先拒
        assertFalse(ItemFilters.isAllowed("minecraft:iron_ore", black, List.of("minecraft:iron_ore")));
    }

    @Test
    void whitelistIsAllowSet() {
        List<String> white = List.of("minecraft:iron_ore", "minecraft:gold_ore");
        assertTrue(ItemFilters.isAllowed("minecraft:iron_ore", NONE, white));
        assertFalse(ItemFilters.isAllowed("minecraft:copper_ore", NONE, white));
        // 空白名单 = 不限制 (不是"全拒")
        assertTrue(ItemFilters.isAllowed("minecraft:copper_ore", NONE, NONE));
    }

    @Test
    void wildcardMatchesWholeMod() {
        List<String> black = List.of("create:*");
        assertFalse(ItemFilters.isAllowed("create:copper_ore", black, NONE));
        assertFalse(ItemFilters.isAllowed("create:zinc_ore", black, NONE));
        assertTrue(ItemFilters.isAllowed("minecraft:iron_ore", black, NONE));
        // 白名单通配同理
        assertTrue(ItemFilters.isAllowed("touhou_little_maid:maid_icon", NONE, List.of("touhou_little_maid:*")));
        assertFalse(ItemFilters.isAllowed("minecraft:iron_ore", NONE, List.of("touhou_little_maid:*")));
    }

    @Test
    void blankAndWhitespaceEntriesAreIgnored() {
        List<String> black = List.of("", "   ", " minecraft:iron_ore ");
        assertFalse(ItemFilters.isAllowed("minecraft:iron_ore", black, NONE), "条目应 trim 后精确匹配");
        assertTrue(ItemFilters.isAllowed("minecraft:gold_ore", black, NONE), "空/空白条目不应误伤");
    }

    @Test
    void perMaidListOverridesGlobal() {
        List<String> global = List.of("minecraft:iron_ore");
        // 单女仆名单非空 ⇒ 覆盖全局 (即使内容"更宽松")
        assertEquals(List.of("minecraft:gold_ore"), ItemFilters.effective(List.of("minecraft:gold_ore"), global));
        assertTrue(ItemFilters.isAllowed("minecraft:iron_ore", ItemFilters.effective(List.of("minecraft:gold_ore"), global), NONE),
                "单女仆名单覆盖后, 全局黑名单不再生效");
        // 单女仆空 ⇒ 回落全局
        assertEquals(global, ItemFilters.effective(List.of(), global));
        assertEquals(global, ItemFilters.effective(null, global));
        // 两者皆空
        assertEquals(List.of(), ItemFilters.effective(null, null));
    }

    @Test
    void nbtRoundTripAndEffectivePair() {
        CompoundTag cfg = new CompoundTag();
        ListTag black = new ListTag();
        black.add(StringTag.valueOf("minecraft:iron_ore"));
        black.add(StringTag.valueOf("create:*"));
        cfg.put(ItemFilters.KEY_BLACKLIST, black);
        ListTag white = new ListTag();
        white.add(StringTag.valueOf("minecraft:gold_ore"));
        cfg.put(ItemFilters.KEY_WHITELIST, white);

        assertEquals(List.of("minecraft:iron_ore", "create:*"), ItemFilters.maidList(cfg, ItemFilters.KEY_BLACKLIST));
        assertEquals(List.of("minecraft:gold_ore"), ItemFilters.maidList(cfg, ItemFilters.KEY_WHITELIST));
        assertEquals(List.of(), ItemFilters.maidList(cfg, "missing_key"));

        var pair = ItemFilters.effectivePair(cfg, NONE, NONE);
        assertEquals(2, pair.size());
        assertEquals(List.of("minecraft:iron_ore", "create:*"), pair.get(0));
        assertEquals(List.of("minecraft:gold_ore"), pair.get(1));
        // 综合: 铁矿被黑名单拒; 金矿在白名单但不在黑 ⇒ 放行; 铜矿不在白名单 ⇒ 拒
        assertFalse(ItemFilters.isAllowed("minecraft:iron_ore", pair.get(0), pair.get(1)));
        assertTrue(ItemFilters.isAllowed("minecraft:gold_ore", pair.get(0), pair.get(1)));
        assertFalse(ItemFilters.isAllowed("minecraft:copper_ore", pair.get(0), pair.get(1)));
    }

    @Test
    void keyNamesAreContract() {
        // 键名是屏 (ItemListConfigScreen) 与消费方 (Furnace/Jukebox/ArmTransfer) 的跨层契约 — 改名 = 老存档静默失效
        assertEquals("blacklist", ItemFilters.KEY_BLACKLIST);
        assertEquals("whitelist", ItemFilters.KEY_WHITELIST);
    }
}
