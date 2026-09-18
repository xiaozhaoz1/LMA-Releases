package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.FarmRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 农田**区域容器接入** (v79.63 自 {@link FarmExecute} 抽出, 该类因而 675 → 约 460 行)。
 *
 * <p><b>职责</b>: 区域绑定箱的取种子 / 存产物 / 放回多余种子 —— "女仆背包 ↔ 区域箱"的搬运;
 * 与"什么时候收/种"的 tick 相位机分离; 无自身静态状态 (时间/区域由调用方传入)。
 *
 * <p><b>依赖</b>: 物品解析走 {@link FarmItems} (同层); 容器走原版 Container / IItemHandler。
 * 背包腾位 ({@code ensureBackpackRoom}) 仍在执行器侧 (要判省流模式), 它调用本类 {@code storeOne} ——
 * 方向为"执行器 → 容器工具", 无环。
 *
 * <p><b>⚠ 源码形态</b>: 本族部分方法签名含平台条件 (`IItemHandler` 双实现) —— Stonecutter 在 common 源里
 * 给每个平台**各留一份完整定义** (`//? if 1.20.1 {` / `//?} else {` / `//?}` 成对行)。**改这些方法必须同时改两支**。
 */
final class FarmRegionContainers {

    private FarmRegionContainers() {}
    /** 从种子源箱取一组指定种子到背包 (区域绑定才有; 无绑定/无种子返回 false) */
    static boolean ensureSeedFromBox(ServerLevel world, EntityMaid maid, FarmRegion region) {
        BlockPos box = region.seedX() != null
                ? new BlockPos(region.seedX(), region.seedY(), region.seedZ()) : null;   // v79.62 per-region 种子箱
        if (box == null) return false;
        // v79.62.1 强制加载种子箱区块 (种子箱可能在区域外/不同区块)
        world.getChunk(box.getX() >> 4, box.getZ() >> 4);
        if (!(world.getBlockEntity(box) instanceof net.minecraft.world.Container cont)) return false;
        net.minecraft.world.item.Item item = FarmItems.resolveSeedItem(region.cropId());
        // 从容器找种子 (cropId 匹配 / 空则任意可种种子) → 一整组移动到女仆背包
        var inv = maid.getAvailableInv(true);
        for (int slot = 0; slot < cont.getContainerSize(); slot++) {
            ItemStack boxStack = cont.getItem(slot);
            if (!FarmItems.matchesSeed(boxStack, item)) continue;
            ItemStack moved = boxStack.copy();
            if (insertInto(inv, moved)) {
                boxStack.shrink(moved.getCount());
                return true;
            }
        }
        return false;
    }

    /**
     * 背包收获产物 → 收获目标箱 (逐格直存兜底; v79.62 用户裁定「只存收获产物」);
     * 无绑定直接返回. 种子/工具/标记物不入箱 (见 {@link #isHarvestProduct}).
     */
    /**
     * 把背包产物存进区域收获箱 + 多余种子放回种子箱。
     *
     * @param lastHarvest 最近收获的区域 (由调用方从 per-maid 运行态取出; null = 无), 用于
     *                    "产物优先进刚收获的那个区域箱" — 本类不持有该状态 (v79.63 抽类时保持无状态)
     * @return true = 走了 lastHarvest 分支 (调用方据此清掉该记录 — 原语义 "一次收获 → 存一次")
     */
    static boolean storeHarvests(ServerLevel world, EntityMaid maid, List<FarmRegion> regions,
                                 FarmRegion lastHarvest) {
        // v79.62.1 多余种子放回种子箱 (每区域: 对应种子保留1组, 其他放种子箱)
        var inv = maid.getAvailableInv(true);
        for (FarmRegion r : regions) {
            storeExcessSeeds(world, inv, r);
        }
        // v79.62.1 产物优先存进「最近收获区域」的收获箱 (女仆刚收获的那个区域)
        // 避免多个同作物区域时产物全进第一个区域箱
        // 只有最近收获区域有收获箱才优先, 否则退回全区域遍历
        if (lastHarvest != null && lastHarvest.hasHarvestBox()) {
            storeToRegionBox(world, inv, lastHarvest, regions);
            return true;   // 一次收获 → 存一次 (清理由调用方做)
        }
        for (FarmRegion r : regions) {
            storeToRegionBox(world, inv, r, regions);
        }
        return false;
    }

    /** v79.62.1 多余种子放回种子箱 — 该区域 cropId 对应种子保留 1 组, 超过部分放回种子箱.
     *  <p>种子即作物 (胡萝卜/土豆/下界疣/可可): 收获产物=种子, 同样保留 1 组当种子, 多余放回 */
    static void storeExcessSeeds(ServerLevel world,
//? if 1.20.1 {
                                         net.minecraftforge.items.IItemHandler inv,
//?} else {
                                         net.neoforged.neoforge.items.IItemHandler inv,
//?}
                                         FarmRegion r) {
        if (!r.hasSeedBox()) return;
        net.minecraft.world.item.Item seedItem = FarmItems.resolveSeedItem(r.cropId());
        if (seedItem == null) return;
        // 统计背包该种子总量
        int total = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(seedItem)) total += s.getCount();
        }
        final int KEEP_GROUP = 64;
        int toStore = total - KEEP_GROUP;
        if (toStore <= 0) return;
        // 放回种子箱
        BlockPos sb = new BlockPos(r.seedX(), r.seedY(), r.seedZ());
        world.getChunk(sb.getX() >> 4, sb.getZ() >> 4);
        if (!(world.getBlockEntity(sb) instanceof net.minecraft.world.Container seedCont)) return;
        int remaining = toStore;
        for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !s.is(seedItem)) continue;
            int take = Math.min(s.getCount(), remaining);
            ItemStack part = s.copy();
            part.setCount(take);
            if (storeOne(seedCont, part)) { s.shrink(take); remaining -= take; }
            else break;
        }
    }

    /** v79.62.1 把女仆背包产物存到指定区域的收获箱 (cropId 匹配产物) */
    static void storeToRegionBox(ServerLevel world,
//? if 1.20.1 {
                                         net.minecraftforge.items.IItemHandler inv,
//?} else {
                                         net.neoforged.neoforge.items.IItemHandler inv,
//?}
                                         FarmRegion r, List<FarmRegion> regions) {
        if (!r.hasHarvestBox()) return;
        BlockPos box = new BlockPos(r.harvestX(), r.harvestY(), r.harvestZ());
        // v79.62.1 强制加载收获箱区块 (收获箱可能在区域外/不同区块 — 否则未加载 getBlockEntity 返回 null)
        world.getChunk(box.getX() >> 4, box.getZ() >> 4);
        if (!(world.getBlockEntity(box) instanceof net.minecraft.world.Container cont)) return;
        // 该区域的种子集合 (产物排除种子)
        java.util.Set<net.minecraft.world.item.Item> seedItems = new java.util.HashSet<>();
        net.minecraft.world.item.Item si = FarmItems.resolveSeedItem(r.cropId());
        if (si != null) seedItems.add(si);
        // 该区域期望的产物 item (cropId 非空时精确匹配; 空时存所有产物)
        net.minecraft.world.item.Item expectedProduct = FarmItems.resolveProductItem(r.cropId());
        // ① 种子即作物: 保留 1 组, 多余进该区域收获箱
        if (expectedProduct != null && FarmItems.isSeedCropItem(new ItemStack(expectedProduct))) {
            final int KEEP_GROUP = 64;
            int total = 0;
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.is(expectedProduct) && FarmItems.isSeedCropItem(s)) total += s.getCount();
            }
            int toStore = total - KEEP_GROUP;
            if (toStore > 0) {
                int remaining = toStore;
                for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (s.isEmpty() || !s.is(expectedProduct) || !FarmItems.isSeedCropItem(s)) continue;
                    int take = Math.min(s.getCount(), remaining);
                    ItemStack part = s.copy();
                    part.setCount(take);
                    if (storeOne(cont, part)) { s.shrink(take); remaining -= take; }
                    else break;
                }
            }
        }
        // ② 纯产物: cropId 非空 → 存对应产物; 空 → 存不匹配其他区域的产物
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isMarkItem(s)) continue;
            if (FarmItems.isSeedItem(s)) continue;
            if (!FarmItems.isHarvestProduct(s, seedItems)) continue;
            if (expectedProduct != null) {
                // cropId 非空: 只存对应产物到本区域箱; 箱满则留给后续同 cropId 区域
                if (!s.is(expectedProduct)) continue;
            } else {
                // cropId 空: 跳过匹配其他区域 cropId 的产物
                if (FarmItems.matchesOtherRegion(s, regions, r)) continue;
            }
            storeOne(cont, s);
        }
    }

    



    

    /** 单格产物尝试塞进容器; 全放成功返回 true (成功/失败都把背包原格扣减为剩余 — 防复制) */
    static boolean storeOne(net.minecraft.world.Container cont, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (int slot = 0; slot < cont.getContainerSize(); slot++) {
            ItemStack in = cont.getItem(slot);
            if (in.isEmpty()) {
                cont.setItem(slot, remain.copy());
                remain.setCount(0);
                break;
            }
            // 同物品可堆叠合并 (产物通常无 NBT — 忽略 NBT 差异)
            if (in.is(remain.getItem()) && in.getCount() < in.getMaxStackSize()) {
                int take = Math.min(remain.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(take);
                remain.shrink(take);
                if (remain.isEmpty()) break;
            }
        }
        // v79.62 修复 (2026-08-20 用户实测): 原只在失败时 setCount — 成功时背包原格未扣减,
        // 物品复制进箱 (storeHarvests 每 tick 跑 → 箱里累积一堆虚空复制品)。成功也要扣减。
        stack.setCount(remain.getCount());
        return remain.isEmpty();
    }

    /** 物品塞入女仆背包 (IItemHandler); 返回 true=全放入 */
    static boolean insertInto(
//? if 1.20.1 {
            net.minecraftforge.items.IItemHandler inv, ItemStack stack) {
//?} else {
            net.neoforged.neoforge.items.IItemHandler inv, ItemStack stack) {
//?}
        ItemStack remain = stack.copy();
        for (int i = 0; i < inv.getSlots() && !remain.isEmpty(); i++) {
            remain = inv.insertItem(i, remain, false);
        }
        return remain.isEmpty();
    }
}
