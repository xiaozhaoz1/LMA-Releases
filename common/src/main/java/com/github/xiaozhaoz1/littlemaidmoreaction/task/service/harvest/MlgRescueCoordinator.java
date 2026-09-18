package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.HandSwap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 摔落自救协调器 (v79.61x 移植自 Numen MLGChain, dwinovo/minecraft-numen LGPL) —
 * 女仆快速坠落时在落点倒水 (或垫软方块), 落进自己的水后把水收回来 (桶不消耗)。
 *
 * <p>女仆适配 (实体差异逐项): 判<b>速度</b>不判落差 (fallDistance 客户端权威坑, 女仆虽为
 * 服务端实体但速度判据更实时, Numen 同款阈值); 放水/收水 = <b>女仆直接世界操作</b>
 * (world.setBlock + 背包水桶↔空桶 — TLM 女仆操作模型: destroyBlock 同款直接世界改,
 * 无玩家权限/视线概念, 2026-08-16 用户裁定; 假人右键链 1.20.1 桶族 use() 视线驱动
 * 双平台行为分裂实测不适用); 五射线探落点 (碰撞箱中心+四角, 防格缝漏); 下界不倒水 (蒸发白扔)。
 *
 * <p>两阶段一条链 (Numen 同款): 摔落 → 够得着就倒水 (软方块兜底) → 落进自己的水 →
 * 沉稳后换空桶收回 (20t 窗口封顶, 到点无条件放手)。状态存 pipelineData (mlg_* 键 —
 * 由 SelfRescuePipeline 传入 pd, cancelPassive → clearPipelineData 自动清理)。
 *
 * <p>纯判据 ({@link #mlgTriggered} / {@link #settled}) 抽静态 — JVM 可测 (MlgJudgeTest)。
 */
public final class MlgRescueCoordinator {

    /** 下落速度 (格/tick) 到 -0.7 = "正在摔" — Numen 原值 (原版重力 0.08/刻 约九刻到达) */
    public static final double MLG_FALL_SPEED = -0.7;
    /** 沉水沉稳速度 — 比阈值慢 = 已落进水里, 与自由落体分得开 */
    public static final double MLG_SETTLED_SPEED = -0.5;
    /** 向下探地最大深度 (格) */
    private static final double PROBE_DEPTH = 40.0;
    /** 放水后收桶窗口 (tick) — 链压过一切, 窗口只够动作本身 */
    private static final int RECLAIM_TICKS = 20;
    /** 放水/垫块可达距离 (格) — 女仆够得着才点 */
    private static final double PLACE_RANGE = 4.0;

    /** pipelineData 键 (SelfRescuePipeline 传入的 pd; 终结自动清理) */
    private static final String KEY_X = "mlg_x";
    private static final String KEY_Y = "mlg_y";
    private static final String KEY_Z = "mlg_z";
    private static final String KEY_RECLAIM = "mlg_reclaim";

    private MlgRescueCoordinator() {}

    // ── 纯判据 (JVM 可测) ──

    /**
     * 摔落自救触发条件: 不在安全态 + 正在快速下落 + 手上有救命物 (水桶/软方块)。
     *
     * @param grounded  脚落地 / 在水中 / 游泳 / 爬梯藤 — 都不算"在摔"
     * @param fallSpeed 竖直速度 (格/tick), 向下为负
     * @param canSave   背包有水桶或软方块 — 没有的话抢了身体也救不了
     */
    public static boolean mlgTriggered(boolean grounded, double fallSpeed, boolean canSave) {
        if (grounded) return false;
        if (!canSave) return false;
        return fallSpeed <= MLG_FALL_SPEED;
    }

    /** 水已在减速 (沉入自己的水) — 该收桶了 */
    public static boolean settled(double fallSpeed) {
        return fallSpeed >= MLG_SETTLED_SPEED;
    }

    // ── 编排入口 ──

    /**
     * 每 tick 驱动 — 收水窗口优先, 其次摔落倒水。
     *
     * @return true = 本 tick 在处理 (摔落中/收水中) — 调用方跳过其余自救动作
     */
    public static boolean tick(ServerLevel world, EntityMaid maid, CompoundTag pd) {
        // 阶段 2: 收水窗口 (落地后)
        int reclaim = pd.getInt(KEY_RECLAIM);
        if (reclaim > 0) {
            pd.putInt(KEY_RECLAIM, reclaim - 1);
            BlockPos placed = readPlaced(pd);
            if (placed == null || !canReclaim(world, maid, placed)) {
                clearMlg(pd);   // 水没了/没空桶/窗口到点 — 放手
                return false;
            }
            if (settled(maid.getDeltaMovement().y)) {
                reclaimWater(world, maid, placed);
                clearMlg(pd);
            }
            return true;
        }
        // 阶段 1: 摔落 → 倒水/垫块
        if (!falling(maid)) return false;
        return clutch(world, maid, pd);
    }

    // ── 阶段实现 ──

    /**
     * 便宜摔落判定 (触发器/管线共用) — grounded 四态 + 速度阈值, 零背包扫描。
     * 真在快速下落的女仆才值得继续查救命物。
     */
    public static boolean fallingFast(EntityMaid maid) {
        boolean grounded = maid.onGround() || maid.isInWater()
                || maid.isSwimming() || maid.onClimbable();
        return !grounded && maid.getDeltaMovement().y <= MLG_FALL_SPEED;
    }

    /** 救命物存在性 (水桶/软方块) — 背包扫描, 只在 fallingFast 命中后调用 */
    public static boolean hasSaveItem(EntityMaid maid) {
        return findWaterBucket(maid) >= 0 || findSoftBlock(maid) >= 0;
    }

    /** 摔落判定 — 便宜判定 + 救命物 (纯函数在 mlgTriggered 内) */
    private static boolean falling(EntityMaid maid) {
        if (!fallingFast(maid)) return false;
        return mlgTriggered(false, maid.getDeltaMovement().y, hasSaveItem(maid));
    }

    /** 摔落中: 探落点, 够得着就倒水 (软方块兜底) — 每刻重探, 够不着等下刻更近 */
    private static boolean clutch(ServerLevel world, EntityMaid maid, CompoundTag pd) {
        BlockPos ground = groundBelow(world, maid);
        if (ground == null) return true;   // 底下四十格没东西 — 继续掉
        if (maid.blockPosition().distSqr(ground) > PLACE_RANGE * PLACE_RANGE) return true;
        BlockPos placed = ground.above();   // 水/软方块落在落点上方那格 (女仆砸落格)
        // 下界水一倒就蒸发 — 只走软方块
        int bucket = world.dimensionType().ultraWarm() ? -1 : findWaterBucket(maid);
        if (bucket >= 0) {
            if (placeWater(world, maid, bucket, placed)) {
                writePlaced(pd, placed);
                pd.putInt(KEY_RECLAIM, RECLAIM_TICKS);
                return true;
            }
        }
        int block = findSoftBlock(maid);
        if (block >= 0) {
            placeSoftBlock(world, maid, block, placed);
        }
        return true;
    }

    /**
     * 女仆直接放水 — 世界 setBlock + 背包水桶→空桶 (TLM 女仆操作模型: 直接世界操作,
     * 不走假人右键链 — destroyBlock 同款语义, 无玩家权限/视线概念; 2026-08-16 用户裁定)。
     */
    private static boolean placeWater(ServerLevel world, EntityMaid maid, int bucketSlot, BlockPos placed) {
        var state = world.getBlockState(placed);
        if (!(state.isAir() || state.canBeReplaced())) return false;
        var inv = maid.getAvailableInv(true);
        ItemStack taken = inv.extractItem(bucketSlot, 1, false);
        if (!taken.is(Items.WATER_BUCKET)) return false;
        ItemStack rest = inv.insertItem(bucketSlot, new ItemStack(Items.BUCKET), false);
        if (!rest.isEmpty()) HandSwap.stashOrDrop(maid, rest);
        world.setBlock(placed, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
        return true;
    }

    /** 女仆直接垫软方块 — 世界 setBlock + 背包消耗 1 块 */
    private static void placeSoftBlock(ServerLevel world, EntityMaid maid, int softSlot, BlockPos placed) {
        var state = world.getBlockState(placed);
        if (!(state.isAir() || state.canBeReplaced())) return;
        var inv = maid.getAvailableInv(true);
        ItemStack s = inv.getStackInSlot(softSlot);
        net.minecraft.world.level.block.Block b = s.is(Items.HAY_BLOCK)
                ? net.minecraft.world.level.block.Blocks.HAY_BLOCK
                : net.minecraft.world.level.block.Blocks.SLIME_BLOCK;
        inv.extractItem(softSlot, 1, false);
        world.setBlock(placed, b.defaultBlockState(), 3);
    }

    /** 收水判定: 那格还是水源, 且手上有空桶 (水桶在放水时已变空桶回手) */
    private static boolean canReclaim(ServerLevel world, EntityMaid maid, BlockPos placed) {
        var state = world.getBlockState(placed);
        return state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource()
                && findEmptyBucket(maid) >= 0;
    }

    /** 女仆直接收水 — 世界置空 + 背包空桶→水桶 */
    private static void reclaimWater(ServerLevel world, EntityMaid maid, BlockPos placed) {
        int empty = findEmptyBucket(maid);
        if (empty < 0) return;
        var inv = maid.getAvailableInv(true);
        ItemStack taken = inv.extractItem(empty, 1, false);
        if (!taken.is(Items.BUCKET)) return;
        ItemStack rest = inv.insertItem(empty, new ItemStack(Items.WATER_BUCKET), false);
        if (!rest.isEmpty()) HandSwap.stashOrDrop(maid, rest);
        world.setBlock(placed, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
    }

    // ── 落点探测 ──

    /**
     * 女仆会砸到的那一格 — 碰撞箱中心+四角各打一条竖直射线取最近 (单一条会从格缝
     * 漏下去, 擦着边掉正是最需要救的)。返回命中实心方块 (水放在其上方)。
     */
    private static BlockPos groundBelow(ServerLevel world, EntityMaid maid) {
        AABB box = maid.getBoundingBox();
        double y = maid.getY();
        Vec3[] origins = {
                new Vec3(maid.getX(), y, maid.getZ()),
                new Vec3(box.minX, y, box.minZ), new Vec3(box.maxX, y, box.minZ),
                new Vec3(box.minX, y, box.maxZ), new Vec3(box.maxX, y, box.maxZ),
        };
        BlockPos best = null;
        double bestDrop = Double.MAX_VALUE;
        for (Vec3 from : origins) {
            BlockHitResult hit = world.clip(new ClipContext(
                    from, from.add(0.0, -PROBE_DEPTH, 0.0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            double drop = y - hit.getLocation().y;
            if (drop < bestDrop) {
                bestDrop = drop;
                best = hit.getBlockPos();
            }
        }
        // v79.62.1 落点避开仙人掌 (砸进仙人掌上方会受伤):
        // 命中仙人掌 → 找水平相邻非仙人掌可站立格作为落点
        if (best != null && world.getBlockState(best).is(net.minecraft.world.level.block.Blocks.CACTUS)) {
            BlockPos safe = safeCactusSpot(world, best);
            if (safe != null) return safe;
        }
        return best;
    }

    /** 找仙人掌旁的安全落点 — 水平四方向找非仙人掌、上方可放水的格 */
    private static BlockPos safeCactusSpot(ServerLevel world, BlockPos cactus) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adj = cactus.relative(dir);
            BlockState adjState = world.getBlockState(adj);
            // 相邻格不是仙人掌且不是空气 (有实心可站立) → 作为安全落点
            if (!adjState.is(net.minecraft.world.level.block.Blocks.CACTUS)
                    && !adjState.isAir() && !adjState.liquid()) {
                return adj;
            }
        }
        return null;  // 周围都是仙人掌/空 — 无法安全落点, 按原逻辑处理
    }

    // ── 槽位查找 ──

    private static int findWaterBucket(EntityMaid maid) {
        return slotWith(maid, Items.WATER_BUCKET);
    }

    private static int findEmptyBucket(EntityMaid maid) {
        return slotWith(maid, Items.BUCKET);
    }

    /** 垫底软方块 (干草/黏液块) — 摔落减伤 */
    private static int findSoftBlock(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(Items.HAY_BLOCK) || s.is(Items.SLIME_BLOCK)) return i;
        }
        return -1;
    }

    private static int slotWith(EntityMaid maid, net.minecraft.world.item.Item item) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).is(item)) return i;
        }
        return -1;
    }

    // ── pipelineData 状态 (终结自动清理) ──

    private static void writePlaced(CompoundTag pd, BlockPos pos) {
        pd.putInt(KEY_X, pos.getX());
        pd.putInt(KEY_Y, pos.getY());
        pd.putInt(KEY_Z, pos.getZ());
    }

    private static BlockPos readPlaced(CompoundTag pd) {
        if (!pd.contains(KEY_X)) return null;
        return new BlockPos(pd.getInt(KEY_X), pd.getInt(KEY_Y), pd.getInt(KEY_Z));
    }

    private static void clearMlg(CompoundTag pd) {
        pd.remove(KEY_X);
        pd.remove(KEY_Y);
        pd.remove(KEY_Z);
        pd.remove(KEY_RECLAIM);
    }
}
