package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.FarmRegion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 农田**物品解析与判定**纯逻辑 (v79.63 自 {@link FarmExecute} 抽出, 该类因而 667 → 约 520 行)。
 *
 * <p><b>职责</b>: 作物/种子 id → Item 解析 · 种子判定 · 收获产物判定 · 跨区域归属判定。
 * **无静态状态、无 tick、无世界写** — 全是"看物品/看注册表"的判定, 故与执行器分离
 * (与 {@code CropRegistry} 的作物判定互补: 那边看 BlockState, 这边看 ItemStack/cropId)。
 */
final class FarmItems {

    private FarmItems() {}
/** 检查物品是否匹配其他区域的 cropId (避免西瓜进只收不种区域的箱) */
    static boolean matchesOtherRegion(ItemStack s, List<FarmRegion> regions, FarmRegion current) {
        for (FarmRegion other : regions) {
            if (other == current) continue;
            if (other.cropId() == null || other.cropId().isBlank()) continue;
            net.minecraft.world.item.Item otherProduct = resolveProductItem(other.cropId());
            if (otherProduct != null && s.is(otherProduct)) return true;
        }
        return false;
    }

/** cropId (种子 id) → 收获产物 Item; 用于区域分离 (小麦种子→小麦, 胡萝卜→胡萝卜, 甜浆果→甜浆果)
     *  <p>种子即作物 (胡萝卜/土豆/下界疣/可可) 的产物就是种子本身; 麦/甜菜/西瓜/南瓜的种子和产物不同 */
    static net.minecraft.world.item.Item resolveProductItem(String cropId) {
        if (cropId == null || cropId.isBlank()) return null;
        // 种子→产物映射表
        return switch (cropId) {
            case "minecraft:wheat_seeds" -> net.minecraft.world.item.Items.WHEAT;
            case "minecraft:beetroot_seeds" -> net.minecraft.world.item.Items.BEETROOT;
            case "minecraft:melon_seeds" -> net.minecraft.world.item.Items.MELON_SLICE;
            case "minecraft:pumpkin_seeds" -> net.minecraft.world.item.Items.PUMPKIN;
            // 种子即作物: 产物 = 种子本身
            case "minecraft:carrot" -> net.minecraft.world.item.Items.CARROT;
            case "minecraft:potato" -> net.minecraft.world.item.Items.POTATO;
            case "minecraft:nether_wart" -> net.minecraft.world.item.Items.NETHER_WART;
            case "minecraft:cocoa_beans" -> net.minecraft.world.item.Items.COCOA_BEANS;
            case "minecraft:sweet_berries" -> net.minecraft.world.item.Items.SWEET_BERRIES;
            case "minecraft:glow_berries" -> net.minecraft.world.item.Items.GLOW_BERRIES;
            case "minecraft:sugar_cane" -> net.minecraft.world.item.Items.SUGAR_CANE;
            case "minecraft:bamboo" -> net.minecraft.world.item.Items.BAMBOO;
            case "minecraft:cactus" -> net.minecraft.world.item.Items.CACTUS;
            case "minecraft:kelp" -> net.minecraft.world.item.Items.KELP;
            default -> null;  // 未知 cropId → null (存所有产物)
        };
    }

/** cropId (物品注册名) → Item; 非法/未注册 → null (双平台注册表) */
    static net.minecraft.world.item.Item resolveSeedItem(String cropId) {
        ResourceLocation rl = ResourceLocation.tryParse(cropId);
        if (rl == null) return null;
//? if 1.20.1 {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
//?} else {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
//?}
    }

/**
     * 判定「种子物品」— ItemNameBlockItem 且方块是可种作物 (TLM TaskNormalFarm.isSeed 同款思路, 纯 vanilla 类).
     * cropId 为空时的兜底: 种子物品/种子箱里的种子能被识别 (不会当产物收进收获箱).
     * 覆盖: 原版 CropBlock (麦/胡萝卜/土豆/甜菜) / StemBlock (西瓜南瓜) / 下界疣 / 可可豆.
     */
    static boolean isSeedItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ItemNameBlockItem itb)) return false;
        net.minecraft.world.level.block.Block b = itb.getBlock();
        return b instanceof net.minecraft.world.level.block.CropBlock
                || b instanceof net.minecraft.world.level.block.StemBlock
                || b instanceof net.minecraft.world.level.block.NetherWartBlock
                || b instanceof net.minecraft.world.level.block.CocoaBlock
                || b == net.minecraft.world.level.block.Blocks.SUGAR_CANE
                || b == net.minecraft.world.level.block.Blocks.BAMBOO
                || b == net.minecraft.world.level.block.Blocks.CACTUS
                || b == net.minecraft.world.level.block.Blocks.KELP;
    }

/**
     * 种子判定统一 (v79.62.1 修复「甜菜不种」): cropId 非空 → 精确匹配, 但若 cropId 物品
     * 本身不是可种种子 (用户把 cropId 设成产物如 beetroot/甜菜根) → 降级 isSeedItem 兜底
     * (找任意可种种子), 避免 findSeed 匹配到收割产物导致 canPlantOn 判定失败不种.
     * cropId 空 → isSeedItem 兜底.
     */
    static boolean matchesSeed(ItemStack s, net.minecraft.world.item.Item item) {
        if (s.isEmpty()) return false;
        if (item != null) {
            // cropId 对应的物品是可种种子 → 精确匹配
            if (isSeedItem(new ItemStack(item))) return s.is(item);
            // cropId 对应的物品不是可种种子 (发光浆果/甜浆果/紫颂花等放置物品) → 不种, 不兜底找其他种子
            return false;
        }
        // cropId 空 → 任意可种种子兜底
        return isSeedItem(s);
    }

/**
     * 种子即作物 (v79.62 用户问「胡萝卜这种既是种子又是作物的」) — ItemNameBlockItem 且
     * 收获产物就是该种子本身: 胡萝卜/土豆/下界疣/可可豆. 这类不能全留背包 (收获箱收不到),
     * 也不能全当产物收 (没种子种) → 保留 1 组当种子, 多余进收获箱.
     */
    static boolean isSeedCropItem(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemNameBlockItem itb)) return false;
        net.minecraft.world.level.block.Block b = itb.getBlock();
        return b == net.minecraft.world.level.block.Blocks.CARROTS
                || b == net.minecraft.world.level.block.Blocks.POTATOES
                || b == net.minecraft.world.level.block.Blocks.NETHER_WART
                || b == net.minecraft.world.level.block.Blocks.COCOA;
    }

/**
     * 收获产物判定 (v79.62 用户裁定「只存收获产物」) — 排除: 空 / 区域指定种子 (留背包重播) /
     * 可损坏物品 (工具/武器/护甲). 产物 (小麦/土豆/果实等) 均不可损坏 → 命中存入.
     * 标记物排除在调用侧 (StickBindUtil.isMarkItem, config 依赖不入纯判定) — 可单测.
     */
    static boolean isHarvestProduct(ItemStack stack, java.util.Set<net.minecraft.world.item.Item> seedItems) {
        if (stack.isEmpty()) return false;
        if (!seedItems.isEmpty() && seedItems.contains(stack.getItem())) return false;
        return !stack.isDamageableItem();
    }}
