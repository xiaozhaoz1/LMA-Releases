package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.FarmRegion;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.CropRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.CropQuery;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.RegionBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 作物区域执行器 (v79.62) — 女仆在绑定区域里收成熟 + 种指定作物 (跨区块续; 区域列表顺序).
 *
 * <p>每 tick 推进一个目标 (收获/播种 1 块), 节流与导航由管线/守卫自持:
 * <ul>
 *   <li>收获: 区域内全部成熟作物 (用户裁定不限指定) — 左键 destroyBlock (默认) 或 右键 FakePlayer 交互</li>
 *   <li>播种: 区域内可种耕地种「区域指定种子」(cropId), 种子不足跳过该区域播种</li>
 * </ul>
 * 掉落: 左键 destroyBlock (TLM 掉落+女仆拾取) / 右键 FakePlayer captureDrops→女仆背包 (LmaPlayerSimulator 既有).
 */
public final class FarmExecute {

    /** 收获/播种工作距离 (sqr) — 对齐 ChainHarvest MINE_DIG_DIST_SQR (4 格) */
    private static final double WORK_DIST_SQR = 16.0;
    /** 右键收的可用距离 (方块交互近距) */
    private static final double INTERACT_DIST_SQR = 6.25;
    /** 诊断日志节流戳 (每 20t 打一次; 定位「收获没补种」) */
    private static long lastDiagTick = Long.MIN_VALUE;

    /** 省流模式戳 (uuid → 下次可工作的 gameTime) — 收获箱满时半分钟(600t)检查一次 */
    private static final java.util.Map<String, Long> SAVE_UNTIL = new java.util.HashMap<>();
    /** v79.62.1 最近收获区域 (uuid → region) — 产物优先存进收获时所在区域的箱 */
    private static final java.util.Map<String, FarmRegion> LAST_HARVEST_REGION = new java.util.HashMap<>();

    static {
        // 女仆卸载清理省流戳 + 最近收获区域 (防 uuid 泄漏)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.MaidUnloadRegistry.register(
                maid -> { SAVE_UNTIL.remove(maid.getStringUUID()); LAST_HARVEST_REGION.remove(maid.getStringUUID()); });
    }

    /** 省流中? (收获箱满 → 600t 内跳过工作) */
    private static boolean inSaveMode(String uuid, long now) {
        Long until = SAVE_UNTIL.get(uuid);
        return until != null && now < until;
    }

    /** 进入省流模式 (600t=30s) + 气泡提醒 */
    private static void enterSaveMode(String uuid, long now, EntityMaid maid) {
        SAVE_UNTIL.put(uuid, now + 600);
        com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi.showInfo(
                maid, "收获箱满了，半分钟后我再看一眼…");
    }

    /**
     * 背包管理 (v79.62 用户裁定) — 保证背包 ≥1 空位 (放收获产物) + 保留种子 (播种).
     * 满时: ① 种子源箱空位>1 → 多余种子放回箱 (腾空位); ② 种子箱也满/没绑 → 丢 1 格种子腾空位;
     * ③ 收获箱满 → 省流模式 (600t 检查一次 + 气泡). 防御: 容器/女仆可能为空.
     */
    private static void ensureBackpackRoom(ServerLevel world, EntityMaid maid, FarmRegion region, long now) {
        String uuid = maid.getStringUUID();
        if (inSaveMode(uuid, now)) return;   // 省流中不处理 (tick 开头也已挡)
        var inv = maid.getAvailableInv(true);
        int free = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) free++;
        }
        if (free >= 1) { /* 有空位 */ }
        else {
            // ① 多余种子放回种子源箱 (箱空位 >1) — cropId 空用 isSeedItem 兜底
            net.minecraft.world.item.Item seedItem = FarmItems.resolveSeedItem(region.cropId());
            boolean moved = false;
            if (region.hasSeedBox()) {
                BlockPos sb = new BlockPos(region.seedX(), region.seedY(), region.seedZ());
                world.getChunk(sb.getX() >> 4, sb.getZ() >> 4);  // 强制加载种子箱区块
                if (world.getBlockEntity(sb) instanceof net.minecraft.world.Container seedCont) {
                    int seedFree = 0;
                    for (int i = 0; i < seedCont.getContainerSize(); i++) {
                        if (seedCont.getItem(i).isEmpty()) seedFree++;
                    }
                    if (seedFree > 1) {
                        for (int i = 0; i < inv.getSlots(); i++) {
                            ItemStack s = inv.getStackInSlot(i);
                            if (FarmItems.matchesSeed(s, seedItem) && FarmRegionContainers.storeOne(seedCont, s)) {
                                moved = true;
                                break; // 放回一组 → 腾出空位
                            }
                        }
                    }
                }
            }
            // ② 种子箱也满/没绑/没有可放回种子 → 丢种子腾空位 (捡作物用)
            // v79.62.1 留种闭环修复: 背包满时只丢「超过 1 组」的多余种子, 保留 1 组续种
            // (原无条件丢一格 — 收获掉的小麦种子被丢光, 无种子箱时无法续种)
            if (!moved) {
                final int KEEP_GROUP = 64;
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (!FarmItems.matchesSeed(s, seedItem)) continue;
                    if (s.getCount() <= KEEP_GROUP) continue;   // ≤1组: 保留续种, 不丢
                    s.setCount(KEEP_GROUP);                      // >1组: 只留 1 组, 丢多余
                    moved = true;
                    break;
                }
            }
            // ③ 收获箱满 → 省流 (产物没处放, 停止工作)
            if (region.hasHarvestBox()) {
                BlockPos hb = new BlockPos(region.harvestX(), region.harvestY(), region.harvestZ());
                world.getChunk(hb.getX() >> 4, hb.getZ() >> 4);  // 强制加载收获箱区块
                if (world.getBlockEntity(hb) instanceof net.minecraft.world.Container hCont) {
                    boolean hFull = true;
                    for (int i = 0; i < hCont.getContainerSize(); i++) {
                        if (hCont.getItem(i).isEmpty()) { hFull = false; break; }
                    }
                    if (hFull) enterSaveMode(uuid, now, maid);
                }
            }
        }
    }

    private FarmExecute() {}

    /** 区域记录 → 纯数据盒 (v79.63: vanilla 层不再认识 storage.FarmRegion — 转换留在本层) */
    private static RegionBox boxOf(FarmRegion region) {
        return RegionBox.of(region.minX(), region.minY(), region.minZ(),
                region.maxX(), region.maxY(), region.maxZ());
    }

    /**
     * 每 tick 推进一步 — 区域内成熟优先收, 无成熟补种, 区域空进下一区域.
     * 无绑定区域 → 空转 (validate 已挡, 双保险).
     */
    public static void tick(ServerLevel world, EntityMaid maid) {
        String uuid = maid.getStringUUID();
        // v79.64: 区域存该女仆 NBT (lma_cfg_farm.regions); 只取**当前维度**有效的区域
        //   (每区域记 dimension — 女仆换维度后那些区域暂停, 用户裁定)
        String dimId = world.dimension().location().toString();
        List<FarmRegion> regions = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage
                .getFor(maid).stream().filter(r -> r.isActiveIn(dimId)).toList();
        if (regions.isEmpty()) return;
        // 省流模式 (收获箱满): 600t 内跳过工作, 到点再检查
        if (inSaveMode(uuid, world.getGameTime())) return;

        // v79.62 修 (用户实测「收获的东西没进收获箱」): 原 storeHarvests 只在区域循环「空闲 tick」
        // 才跑 — 女仆每 tick 都在收/种 (return early), 产物积在背包不进箱。改为每 tick 先存产物。
        // v79.63: 最近收获区域由本层从 per-maid 运行态取出 (容器工具保持无状态); 返回 true = 已消费 → 清记录
        FarmRegion lastHarvest = LAST_HARVEST_REGION.get(maid.getStringUUID());
        if (FarmRegionContainers.storeHarvests(world, maid, regions, lastHarvest)) {
            LAST_HARVEST_REGION.remove(maid.getStringUUID());
        }

        // v79.62.2 区块工作制接管: HOME 模式大面积农田工作区跟随 (expandHomeToFarm 退役) —
        // 由 LmaFlowCoordinationBehavior 的 ChunkWorkArea 处理 (home 跟随工作区块 + 内置区块缓存).

        for (FarmRegion region : regions) {
            // 背包管理 (v79.62 用户裁定): 保 1 空位; 满时回种/丢种腾空位; 收获箱满 → 省流
            long gt = world.getGameTime();
            ensureBackpackRoom(world, maid, region, gt);
            if (inSaveMode(uuid, gt)) return;   // 省流中被触发 → 本 tick 收手
            // 诊断 (节流 20t — 定位「收获了没补种」: cropId/箱/成熟/种子/可种耕地)

            if (gt - lastDiagTick >= 20) {
                lastDiagTick = gt;
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER
                        .info("[LMA/Farm] diag crop='{}' seedBox={} harvestBox={}",
                                region.cropId(), region.hasSeedBox(), region.hasHarvestBox());
            }
            // ① 收成熟 (区域内全部) — 每 tick 一个
            List<BlockPos> mature = CropQuery.scanMature(world, maid.blockPosition(), boxOf(region), maid.blockPosition());
            if (gt - lastDiagTick >= 20) {
                lastDiagTick = gt;
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER
                        .info("[LMA/Farm] diag region='{}' cropId='{}' mature={} mode={}",
                                region.displayName(), region.cropId(), mature.size(), region.harvestMode());
            }
            for (BlockPos crop : mature) {
                if (tryHarvest(world, maid, crop, region)) return;   // 处理了一个即停 (下 tick 续)
            }
            // ② 无成熟 → 播种指定作物 (cropId 空 = 只收不种, 跳过播种)
            if (region.cropId() == null || region.cropId().isBlank()) continue;
            ItemStack seed = findSeed(maid, region);
            if (seed.isEmpty()) {
                if (FarmRegionContainers.ensureSeedFromBox(world, maid, region)) {
                    seed = findSeed(maid, region);
                }
            }
            if (seed.isEmpty()) {
                if (gt - lastDiagTick >= 20) {
                    lastDiagTick = gt;
                    com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER
                            .info("[LMA/Farm] diag 无种子 crop='{}' — 背包/手/种子源箱均无", region.cropId());
                }
                continue; // 无种子 → 下一区域 (不卡)
            }
            List<BlockPos> plantable = CropQuery.scanPlantable(world, boxOf(region), maid.blockPosition(), seed);
            if (gt - lastDiagTick >= 20) {
                lastDiagTick = gt;
                com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER
                        .info("[LMA/Farm] diag 有种子 crop='{}' seed={} 可种耕地={}",
                                region.cropId(), seed.getItem(), plantable.size());
            }
            for (BlockPos base : plantable) {
                if (tryPlant(world, maid, base, seed, region)) return;
            }
        }
    }

    // ── 收获 ──

    private static boolean tryHarvest(ServerLevel world, EntityMaid maid, BlockPos crop, FarmRegion region) {
        BlockState cropState = world.getBlockState(crop);
        // v79.62.1 茎摘通道: 茎方块 (StemBlock/AttachedStemBlock) → 按 facing 找相邻果实破坏, 茎保留
        // (茎方块的 isMatureCrop 返回 false, 不走成熟判定; 但缓存可能把茎当 CROP 匹配)
        if (CropRegistry.isStemCrop(cropState)) {
            double d = crop.distSqr(maid.blockPosition());
            if (d > WORK_DIST_SQR) {
                navigate(world, maid, crop);
                return true;
            }
            boolean ok = harvestStemFruit(world, maid, crop, cropState);
            if (ok) {
                // v79.62.1 茎摘成功也记录收获区域 (产物进对应区域的箱)
                LAST_HARVEST_REGION.put(maid.getStringUUID(), region);
                com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache
                        .invalidateBlock(world, crop);
            }
            return ok;
        }
        // v79.62 修复 (用户实测「一直对着一块地种」): 缓存是粗匹配提示 (setBlock 不失效, #238),
        // 执行前必须实时验证该位置仍是成熟作物 — 否则刚播种的 AGE0 会被过期 CROP 缓存当成熟
        // 破坏再种, 形成「种→收→种」死循环
        if (!CropRegistry.isMatureCrop(world, crop, cropState)) {
            // 过期粗匹配 (已收/已种) → 从缓存剔除, 避免每 tick 重复瞄准同一块
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache
                    .invalidateBlock(world, crop);
            return false;
        }
        double d = crop.distSqr(maid.blockPosition());
        // v79.62.1 收获分流: region.isRightHarvest 强制覆盖 (向后兼容); 否则按 CropRegistry 自动判定
        boolean useRight = region.isRightHarvest() || CropRegistry.isRightClickHarvestable(cropState);
        double workDist = useRight ? INTERACT_DIST_SQR : WORK_DIST_SQR;
        if (d > workDist) {
            navigate(world, maid, crop);   // 未到近 → 导航
            return true;                    // 返回「已消耗本 tick」避免同 tick 导航+动作
        }
        boolean ok = useRight
                ? harvestRight(world, maid, crop)
                : harvestLeft(world, maid, crop);
        if (ok) {
            LAST_HARVEST_REGION.put(maid.getStringUUID(), region);
            com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
            // 收获成功 → 更新缓存: 该位置不再是成熟作物 (防下一 tick 重复瞄准)
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache
                    .invalidateBlock(world, crop);
        }
        return ok;
    }

    /** 左键收获 — TLM dropResourcesToMaidInv (掉落直接进女仆背包, 背包满才落地; 参考 TLM
     *  ISpecialCropHandler.harvest + MaidHealSelfTask 拾取链); swing 摆臂.
     *  <p>v79.62.1 改: 原 destroyBlock 掉落进世界靠 TLM 拾取任务捡 — farm 执行时拾取任务可能被
     *  压制, 掉落物没人捡; 改为直接捕获掉落进背包 (与右键 captureDrops 行为一致, 后续 storeHarvests
     *  每 tick 从背包分流到收获箱). 背包满时剩余落地 (dropResourcesToMaidInv 内 popResource). */
    private static boolean harvestLeft(ServerLevel world, EntityMaid maid, BlockPos crop) {
        BlockState st = world.getBlockState(crop);
        if (st.isAir()) return false;
        if (!maid.canDestroyBlock(crop)) return false;
        // TLM dropResourcesToMaidInv — 方块掉落直接塞进女仆背包, 剩余落地
        maid.dropResourcesToMaidInv(st, world, crop, st.hasBlockEntity() ? world.getBlockEntity(crop) : null,
                maid, ItemStack.EMPTY);
        // 破坏方块本体 (掉落已捕获, setBlock 空气)
        world.setBlock(crop, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        maid.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** 右键收获 — LmaPlayerSimulator RIGHT_CLICK (捕获掉落→女仆背包; 右键收获作物/果树果实) */
    private static boolean harvestRight(ServerLevel world, EntityMaid maid, BlockPos crop) {
        BlockState st = world.getBlockState(crop);
        if (st.isAir()) return false;
        boolean ok = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.FakePlayerInteract
                .rightClick(world, maid, crop, Direction.UP);
        if (ok) maid.swing(InteractionHand.MAIN_HAND);
        return ok;
    }

    /** v79.62.1 茎摘果实 — StemBlock/AttachedStemBlock → 按 facing 找相邻果实破坏, 茎保留
     *  <p>AttachedStemBlock: FACING 指向果实方向; StemBlock(AGE=7): 四方向找果实 */
    private static boolean harvestStemFruit(ServerLevel world, EntityMaid maid, BlockPos stem, BlockState stemState) {
        boolean ok = CropRegistry.harvestStemFruit(world, stem, stemState);
        if (ok) maid.swing(InteractionHand.MAIN_HAND);
        return ok;
    }

    // ── 播种 ──

    private static boolean tryPlant(ServerLevel world, EntityMaid maid, BlockPos base, ItemStack seed,
                                   com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.FarmRegion region) {
        BlockState baseState = world.getBlockState(base);
        if (!CropRegistry.canPlantOn(baseState, seed)) return false;
        if (!(seed.getItem() instanceof ItemNameBlockItem item)) return false;
        // 可可豆特殊: 种在原木侧面 (参考 TLM TaskCocoa)
        if (item.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock) {
            return tryPlantCocoa(world, maid, base, seed, region);
        }
        // 普通作物: 种在 base 上方
        BlockPos cropPos = base.above();
        if (!world.getBlockState(cropPos).canBeReplaced()) {
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache
                    .invalidateBlock(world, cropPos);
            return false;
        }
        double d = cropPos.distSqr(maid.blockPosition());
        if (d > WORK_DIST_SQR) {
            navigate(world, maid, cropPos);
            return true;
        }
        BlockState plantState = item.getBlock().defaultBlockState();
        world.setBlock(cropPos, plantState, 3);
        seed.shrink(1);
        maid.swing(InteractionHand.MAIN_HAND);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache
                .invalidateBlock(world, cropPos);
        return true;
    }

    /** 可可豆种植 — 参考TLM: base 是丛林原木 → 四方向找空位放可可豆 (FACING=方向反) */
    private static boolean tryPlantCocoa(ServerLevel world, EntityMaid maid, BlockPos logPos, ItemStack seed,
                                         com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.FarmRegion region) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sidePos = logPos.relative(dir);
            // 附着位置必须在区域内 (用户裁定: 附着种植还是在区域内)
            if (sidePos.getX() < region.minX() || sidePos.getX() > region.maxX()
                    || sidePos.getZ() < region.minZ() || sidePos.getZ() > region.maxZ()
                    || sidePos.getY() < region.minY() - 1 || sidePos.getY() > region.maxY() + 1) continue;
            if (world.getBlockState(sidePos).canBeReplaced()) {
                double d = sidePos.distSqr(maid.blockPosition());
                if (d > WORK_DIST_SQR) {
                    navigate(world, maid, sidePos);
                    return true;
                }
                BlockState cocoaState = net.minecraft.world.level.block.Blocks.COCOA
                        .defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, dir.getOpposite());
                world.setBlock(sidePos, cocoaState, 3);
                seed.shrink(1);
                maid.swing(InteractionHand.MAIN_HAND);
                com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache
                        .invalidateBlock(world, sidePos);
                return true;
            }
        }
        return false;  // 四方向都满了
    }

    

    

    

    

    

    /** 背包/手找种子 (getAvailableInv 全槽含主副手+背包) — cropId 精确匹配, 空则任意可种种子兜底 */
    private static ItemStack findSeed(EntityMaid maid, FarmRegion region) {
        net.minecraft.world.item.Item item = FarmItems.resolveSeedItem(region.cropId());
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (FarmItems.matchesSeed(s, item)) return s;
        }
        return ItemStack.EMPTY;
    }

    // ── 容器绑定接入 (v79.62 区域制 — 每区域自己的种子源箱/收获目标箱) ──


    // ── 导航 ──

    private static void navigate(ServerLevel world, EntityMaid maid, BlockPos target) {
        // v79.62 修 (用户实测「种地没有寻路」): 原只设 NAV_TARGET (无消费方驱动移动) → 女仆不走。
        // 改设 TLM WALK_TARGET (BehaviorUtils.setWalkAndLookTargetMemories) 驱动实际走路。
        net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(
                maid, target, 1.0F, 0);
        NavigationMemory.setNavTarget(maid, target);   // 保留 (NavProgressGuard 追踪用)
        com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard
                .track(maid, target, world.getGameTime());
    }

}
