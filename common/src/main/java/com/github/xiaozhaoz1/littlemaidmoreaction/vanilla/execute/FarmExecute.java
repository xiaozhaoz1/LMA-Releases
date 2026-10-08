package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.FarmRegion;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.CropQuery;
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
            net.minecraft.world.item.Item seedItem = resolveSeedItem(region.cropId());
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
                            if (matchesSeed(s, seedItem) && storeOne(seedCont, s)) {
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
                    if (!matchesSeed(s, seedItem)) continue;
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

    /**
     * 每 tick 推进一步 — 区域内成熟优先收, 无成熟补种, 区域空进下一区域.
     * 无绑定区域 → 空转 (validate 已挡, 双保险).
     */
    public static void tick(ServerLevel world, EntityMaid maid) {
        String uuid = maid.getStringUUID();
        List<FarmRegion> regions = com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.getFor(uuid);
        if (regions.isEmpty()) return;
        // 省流模式 (收获箱满): 600t 内跳过工作, 到点再检查
        if (inSaveMode(uuid, world.getGameTime())) return;

        // v79.62 修 (用户实测「收获的东西没进收获箱」): 原 storeHarvests 只在区域循环「空闲 tick」
        // 才跑 — 女仆每 tick 都在收/种 (return early), 产物积在背包不进箱。改为每 tick 先存产物。
        storeHarvests(world, maid, regions);

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
            List<BlockPos> mature = CropQuery.scanMature(world, maid.blockPosition(), region, maid.blockPosition());
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
                if (ensureSeedFromBox(world, maid, region)) {
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
            List<BlockPos> plantable = CropQuery.scanPlantable(world, region, maid.blockPosition(), seed);
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
                com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
                        .invalidateBlock(world, crop);
            }
            return ok;
        }
        // v79.62 修复 (用户实测「一直对着一块地种」): 缓存是粗匹配提示 (setBlock 不失效, #238),
        // 执行前必须实时验证该位置仍是成熟作物 — 否则刚播种的 AGE0 会被过期 CROP 缓存当成熟
        // 破坏再种, 形成「种→收→种」死循环
        if (!CropRegistry.isMatureCrop(world, crop, cropState)) {
            // 过期粗匹配 (已收/已种) → 从缓存剔除, 避免每 tick 重复瞄准同一块
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
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
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
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
                                   com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.FarmRegion region) {
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
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
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
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
                .invalidateBlock(world, cropPos);
        return true;
    }

    /** 可可豆种植 — 参考TLM: base 是丛林原木 → 四方向找空位放可可豆 (FACING=方向反) */
    private static boolean tryPlantCocoa(ServerLevel world, EntityMaid maid, BlockPos logPos, ItemStack seed,
                                         com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.FarmRegion region) {
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
                com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
                        .invalidateBlock(world, sidePos);
                return true;
            }
        }
        return false;  // 四方向都满了
    }

    /** 检查物品是否匹配其他区域的 cropId (避免西瓜进只收不种区域的箱) */
    private static boolean matchesOtherRegion(ItemStack s, List<FarmRegion> regions, FarmRegion current) {
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
    private static net.minecraft.world.item.Item resolveProductItem(String cropId) {
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
    private static net.minecraft.world.item.Item resolveSeedItem(String cropId) {
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
    private static boolean isSeedItem(ItemStack stack) {
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
    private static boolean matchesSeed(ItemStack s, net.minecraft.world.item.Item item) {
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

    /** 背包/手找种子 (getAvailableInv 全槽含主副手+背包) — cropId 精确匹配, 空则任意可种种子兜底 */
    private static ItemStack findSeed(EntityMaid maid, FarmRegion region) {
        net.minecraft.world.item.Item item = resolveSeedItem(region.cropId());
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (matchesSeed(s, item)) return s;
        }
        return ItemStack.EMPTY;
    }

    // ── 容器绑定接入 (v79.62 区域制 — 每区域自己的种子源箱/收获目标箱) ──

    /** 从种子源箱取一组指定种子到背包 (区域绑定才有; 无绑定/无种子返回 false) */
    private static boolean ensureSeedFromBox(ServerLevel world, EntityMaid maid, FarmRegion region) {
        BlockPos box = region.seedX() != null
                ? new BlockPos(region.seedX(), region.seedY(), region.seedZ()) : null;   // v79.62 per-region 种子箱
        if (box == null) return false;
        // v79.62.1 强制加载种子箱区块 (种子箱可能在区域外/不同区块)
        world.getChunk(box.getX() >> 4, box.getZ() >> 4);
        if (!(world.getBlockEntity(box) instanceof net.minecraft.world.Container cont)) return false;
        net.minecraft.world.item.Item item = resolveSeedItem(region.cropId());
        // 从容器找种子 (cropId 匹配 / 空则任意可种种子) → 一整组移动到女仆背包
        var inv = maid.getAvailableInv(true);
        for (int slot = 0; slot < cont.getContainerSize(); slot++) {
            ItemStack boxStack = cont.getItem(slot);
            if (!matchesSeed(boxStack, item)) continue;
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
    private static void storeHarvests(ServerLevel world, EntityMaid maid, List<FarmRegion> regions) {
        // v79.62.1 多余种子放回种子箱 (每区域: 对应种子保留1组, 其他放种子箱)
        var inv = maid.getAvailableInv(true);
        for (FarmRegion r : regions) {
            storeExcessSeeds(world, inv, r);
        }
        // v79.62.1 产物优先存进「最近收获区域」的收获箱 (女仆刚收获的那个区域)
        // 避免多个同作物区域时产物全进第一个区域箱
        FarmRegion lastHarvest = LAST_HARVEST_REGION.get(maid.getStringUUID());
        // 只有最近收获区域有收获箱才优先, 否则退回全区域遍历
        if (lastHarvest != null && lastHarvest.hasHarvestBox()) {
            storeToRegionBox(world, inv, lastHarvest, regions);
            LAST_HARVEST_REGION.remove(maid.getStringUUID());  // 一次收获 → 存一次
            return;
        }
        for (FarmRegion r : regions) {
            storeToRegionBox(world, inv, r, regions);
        }
    }

    /** v79.62.1 多余种子放回种子箱 — 该区域 cropId 对应种子保留 1 组, 超过部分放回种子箱.
     *  <p>种子即作物 (胡萝卜/土豆/下界疣/可可): 收获产物=种子, 同样保留 1 组当种子, 多余放回 */
    private static void storeExcessSeeds(ServerLevel world,
//? if 1.20.1 {
                                         net.minecraftforge.items.IItemHandler inv,
//?} else {
                                         net.neoforged.neoforge.items.IItemHandler inv,
//?}
                                         FarmRegion r) {
        if (!r.hasSeedBox()) return;
        net.minecraft.world.item.Item seedItem = resolveSeedItem(r.cropId());
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
    private static void storeToRegionBox(ServerLevel world,
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
        net.minecraft.world.item.Item si = resolveSeedItem(r.cropId());
        if (si != null) seedItems.add(si);
        // 该区域期望的产物 item (cropId 非空时精确匹配; 空时存所有产物)
        net.minecraft.world.item.Item expectedProduct = resolveProductItem(r.cropId());
        // ① 种子即作物: 保留 1 组, 多余进该区域收获箱
        if (expectedProduct != null && isSeedCropItem(new ItemStack(expectedProduct))) {
            final int KEEP_GROUP = 64;
            int total = 0;
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.is(expectedProduct) && isSeedCropItem(s)) total += s.getCount();
            }
            int toStore = total - KEEP_GROUP;
            if (toStore > 0) {
                int remaining = toStore;
                for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (s.isEmpty() || !s.is(expectedProduct) || !isSeedCropItem(s)) continue;
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
            if (isSeedItem(s)) continue;
            if (!isHarvestProduct(s, seedItems)) continue;
            if (expectedProduct != null) {
                // cropId 非空: 只存对应产物到本区域箱; 箱满则留给后续同 cropId 区域
                if (!s.is(expectedProduct)) continue;
            } else {
                // cropId 空: 跳过匹配其他区域 cropId 的产物
                if (matchesOtherRegion(s, regions, r)) continue;
            }
            storeOne(cont, s);
        }
    }

    /**
     * 种子即作物 (v79.62 用户问「胡萝卜这种既是种子又是作物的」) — ItemNameBlockItem 且
     * 收获产物就是该种子本身: 胡萝卜/土豆/下界疣/可可豆. 这类不能全留背包 (收获箱收不到),
     * 也不能全当产物收 (没种子种) → 保留 1 组当种子, 多余进收获箱.
     */
    private static boolean isSeedCropItem(ItemStack stack) {
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
    }

    /** 单格产物尝试塞进容器; 全放成功返回 true (成功/失败都把背包原格扣减为剩余 — 防复制) */
    private static boolean storeOne(net.minecraft.world.Container cont, ItemStack stack) {
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
    private static boolean insertInto(
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
