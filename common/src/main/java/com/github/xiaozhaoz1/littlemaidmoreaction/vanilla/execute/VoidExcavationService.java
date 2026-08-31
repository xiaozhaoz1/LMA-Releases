package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolJudge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v79.62 挖空置域 IO 层 — 逐层挖掘 + 工具替换 + 液体填充 + 传送 + 区块加载。
 *
 * <p>纯业务原语 (五层尺: pipeline → service → 单拍), 供 {@code VoidExcavationPipeline} 每 tick 驱动。
 * 设计: 逐层下降 (y 从高到低), 每层遍历 x/z 全挖; 挖出方块经 Block.getDrops 收集进女仆背包
 * (由容器层转存输出箱), 不产生地上实体掉落 (空置域海量方块, 落地堆积卡顿).
 *
 * <p>液体: 含水方块/流动液体 → 先放个方块填掉 (铺路), 下一轮再挖该方块 (天然避免回填).
 * 传送: 女仆无法到达目标格 → moveTo 传送到方块上方/旁边 (逐层下降每层传送).
 * 区块: 工作区块 setChunkForced 强制加载 (量大 + 多女仆).
 */
public final class VoidExcavationService {

    /** 挖掘范围垂直上限 (世界最高可挖层) — 动态: maxBuildHeight-1 (1.21.1=319, 含最高层方块) */
    public static int maxY(ServerLevel world) { return world.getMaxBuildHeight() - 1; }
    /** 挖掘范围垂直下限 (基岩层) — 动态: minBuildHeight (1.21.1=-64, 1.20.1=-64; 用户裁定: 高版本基岩层 y 负) */
    public static int minY(ServerLevel world) { return world.getMinBuildHeight(); }

    private VoidExcavationService() {}

    /**
     * 挖掉一格方块 — 工具替换 + 液体填充 + 破坏 + 收集掉落进女仆背包.
     * v79.62.1 销毁名单过滤 (用户裁定): 掉落进背包前按单女仆配置过滤 —
     * 名单内物品挖出即销毁 (消失, 不进背包不落地); 名单外进背包 (溢出落地).
     * @return true = 该格处理完成 (挖掉/填掉待下轮), false = 无法处理
     */
    public static boolean tryDig(ServerLevel world, EntityMaid maid, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return true;   // 已空

        // 液体 (水/岩浆) → 直接空气化 + 破坏粒子 (v79.62.2 用户裁定: 不填方块,
        // 破坏特效即时反馈; 不再挖 DIRT 一轮 — 液体格直接清空)
        if (!state.getFluidState().isEmpty()) {
            world.destroyBlock(pos, false);   // 液体无掉落, 直接移除
            // 破坏粒子 (levelEvent 2001 = 方块破坏粒子, data = 方块状态 id)
            world.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
            return true;
        }

        // 基岩显式跳过 (v79.62.1 用户裁定: 基岩不可挖, 不挖不收集)
        if (state.is(net.minecraft.world.level.block.Blocks.BEDROCK)) return true;

        // 不可挖掘 (屏障/传送门等) → TLM canDestroyBlock 门控, 直接跳过
        if (!maid.canDestroyBlock(pos)) return true;

        // 工具替换 (按目标方块换镐/锹/斧)
        ensureToolFor(maid, state);
        ItemStack tool = maid.getMainHandItem();

        // v79.62.1 手动掉落 (替代 TLM destroyBlock 进包 — 需要销毁名单过滤):
        // 先取掉落列表 (Block.getDrops) → world.destroyBlock(pos, false) 挖掉不产掉落
        // → 逐项过滤: 名单内物品直接销毁 (消失), 其余 insertItem 进背包 (溢出落地).
        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(state, world, pos,
                state.hasBlockEntity() ? world.getBlockEntity(pos) : null, maid, tool);
        boolean removed = world.destroyBlock(pos, false);
        if (removed) {
            maid.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            // v79.62.1 修工具不掉耐久: destroyBlock 是直接世界操作, 不经玩家挖矿流程不扣耐久 —
            // 补 1 点/格 (对齐 DigThrough/SelfRescue); 耗完 vanilla 自动移除, 下一格 ensureToolFor 换下一把.
            if (!tool.isEmpty() && tool.isDamageableItem()) {
//? if 1.20.1 {
                tool.hurtAndBreak(1, maid,
                        e -> e.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
//?} else {
                tool.hurtAndBreak(1, maid, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
//?}
            }
            // v79.62.1 用户裁定 (黑名单→销毁名单): 名单 = 单女仆 pipelineConfig 优先, 回退全局 (Cloth 任务自定义).
            com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                    .cfgOrCreate(maid, "void_excavation");
            java.util.Set<String> destroyList = new java.util.HashSet<>();
            net.minecraft.nbt.Tag destroyListTag = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData
                    .cfg(maid, "void_excavation").get("destroy_list");
            if (destroyListTag instanceof net.minecraft.nbt.ListTag listTag) {
                for (net.minecraft.nbt.Tag t : listTag) destroyList.add(t.getAsString());
            }
            if (destroyList.isEmpty() && !com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig
                    .VOID_DESTROY_LIST.get().isEmpty()) {
                // 单女仆未配销毁名单 → 用全局 (Cloth 任务自定义)
                destroyList.addAll(com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig
                        .VOID_DESTROY_LIST.get());
            }
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(drop.getItem()).toString();
                if (destroyList.contains(id)) continue;   // 销毁名单 → 直接消失 (不进背包不落地)
                // 名单外 → 遍历背包所有槽插入 (v79.62.2 修: 原只插槽 0 → 槽0满但其他槽空时
                // 溢出落地 = 用户实证「身上没满却掉东西」; 遍历插到空槽/可堆叠槽, 真满才落地)
                var inv = maid.getAvailableInv(false);
                ItemStack remain = drop;
                for (int slot = 0; slot < inv.getSlots(); slot++) {
                    remain = inv.insertItem(slot, remain, false);
                    if (remain.isEmpty()) break;
                }
                if (!remain.isEmpty()) maid.spawnAtLocation(remain, 0.5f);
            }
        }
        return removed;
    }

    /** 按目标方块换合适工具 (对齐 ChainHarvestExecute.ensureToolFor — ToolJudge 判断) */
    private static void ensureToolFor(EntityMaid maid, BlockState state) {
        ToolJudge.ToolType need = ToolJudge.suitableToolType(state);
        if (need == ToolJudge.ToolType.NONE) return;
        if (ToolJudge.isSuitableUsable(maid.getMainHandItem(), state, 1)) return;
        ToolJudge.selectBestForBlock(maid, state, 1)
                .ifPresent(p -> com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.HandSwap.swapTo(maid, p.slot()));
    }

    /** 女仆是否在该位置附近 (可挖掘距离内, v79.62.1: 4 格 — TLM destroyBlock 无距离限制).
     *  v79.62.1 修"挖不到": 原用 3D distSqr 含垂直差 — 女仆站 target.above() 挖 target
     *  (垂直差 1) + 水平 4 格 = 3D 5 格 > 4 → 永远不 near → 不挖. 改只算水平距离
     *  (垂直差是正常姿态: 站上层挖下层). */
    public static boolean near(EntityMaid maid, BlockPos pos) {
        double dx = maid.getX() - (pos.getX() + 0.5);
        double dz = maid.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= 16.0;   // 水平 4 格² (忽略垂直)
    }

    /** 站立格是否可站 — 站立格自身空气/可站 + 脚下 (y-1) 是实体方块.
     *  v79.62.1 修卡方格: 原只查脚下, 站立格自身是方块时女仆传进方块里卡住出不来. */
    private static boolean isStandable(ServerLevel world, BlockPos stand) {
        if (stand.getY() < world.getMinBuildHeight() || stand.getY() >= world.getMaxBuildHeight()) return false;
        // v79.62.1 修卡顿: 区块未加载 → false (getBlockState 触发生成会卡主线程)
        if (!world.hasChunk(stand.getX() >> 4, stand.getZ() >> 4)) return false;
        // 站立格自身必须可站 (空气/无碰撞) — 防传进方块卡住 (用户双女仆实证: 挖两格卡里面出不来)
        BlockState self = world.getBlockState(stand);
        if (!self.getCollisionShape(world, stand).isEmpty()) return false;
        if (!self.getFluidState().isEmpty()) return false;
        // 脚下必须有支撑
        BlockState below = world.getBlockState(stand.below());
        if (below.isAir()) return false;
        if (!below.getFluidState().isEmpty()) return false;
        return below.getCollisionShape(world, stand.below()).isEmpty() == false;
    }

    /** 传送到站立格 (v79.62.1: pos = 女仆脚应处格, 脚下有支撑; 不再 +1 导致悬空).
     *  传参语义: pos = 女仆脚应处格 — 脚在 pos.y.
     *  v79.62.1 修卡顿: 目标区块未生成 → 不传送 (maid.teleportTo 同步加载目标区块,
     *  频繁传送至未生成区块 = 同步生成卡主线程; 跳过等区块生成后下轮再传).
     *  v79.62.1 修传送失效 (用户实证): 纯跳过 → 目标区块若一直不生成则永不传送;
     *  改 forceChunk 异步预加载目标区块, 区块加载中返回 false (调用方下 tick 重试),
     *  加载完成后真正传送. @return true = 传送执行, false = 目标区块加载中/无支撑 (下 tick 重试). */
    public static boolean teleportTo(ServerLevel world, EntityMaid maid, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!world.hasChunk(cx, cz)) {
            // 区块未生成 → 异步预加载 (ChunkBuilder 后台, 不阻塞主线程), 本 tick 不传 (下 tick 重试)
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.VoidExcavationChunkManager
                    .forceChunk(world, cx, cz, true);
            return false;
        }
        // v79.6x 修传空中摔落: 脚下必须有实心碰撞支撑 (非空气/非流体); 无支撑跳过, 交上层重找落脚/导航
        if (pos.getY() <= world.getMinBuildHeight()) return false;
        BlockState below = world.getBlockState(pos.below());
        if (below.isAir() || !below.getFluidState().isEmpty()
                || below.getCollisionShape(world, pos.below()).isEmpty()) return false;
        return doTeleport(maid, pos);
    }

    /** 挖空专用传送 (v79.62.1 修传送失效): 挖空置域往下挖, cursor 附近全是已挖空的空气,
     *  普通 teleportTo 要求脚下实心 → 永远失败. 本方法允许站空气 (女仆传送到 target.above()
     *  站上层挖下层, 挖掉后自然下落一层). 传参语义: pos = 女仆脚应处格 (target.above()).
     *  @return true = 传送执行, false = 目标区块加载中 (下 tick 重试). */
    public static boolean teleportToVoid(ServerLevel world, EntityMaid maid, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!world.hasChunk(cx, cz)) {
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.VoidExcavationChunkManager
                    .forceChunk(world, cx, cz, true);
            return false;
        }
        if (pos.getY() <= world.getMinBuildHeight()) return false;
        return doTeleport(maid, pos);
    }

    private static boolean doTeleport(EntityMaid maid, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        maid.teleportTo(x, y, z);
        maid.getNavigation().stop();
        return true;
    }

    /** 导航到目标 (走不到再传送 — 先导航) */
    public static void navigateTo(EntityMaid maid, BlockPos pos) {
        com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil.navigateTo(maid, pos);
    }

    /** 到达目标列判定 (距目标 < 3 格) */
    public static boolean arrivedAt(EntityMaid maid, BlockPos pos) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil.arrived(maid, pos);
    }

    /**
     * 挖掘节拍 (v79.62.1 用户裁定: 按方块硬度, 原版公式) —
     * 原版: destroyProgress = digSpeed / destroySpeed / 30 → tick数 = destroySpeed*30/digSpeed.
     * digSpeed = 主手工具 getDestroySpeed(state) (合适工具倍率 2-9, 不合适/空手=1);
     * destroySpeed = 方块硬度 getDestroySpeed(world,pos).
     * 好感等级 0-3 加速: ticks ÷ (1 + level×0.25) → 1/1.25/1.5/1.75.
     */
    public static int digIntervalTicks(ServerLevel world, EntityMaid maid, BlockPos pos, BlockState state) {
        float digSpeed = maid.getMainHandItem().getDestroySpeed(state);
        float destroySpeed = state.getDestroySpeed(world, pos);
        int base = Math.max(1, (int) (destroySpeed * 30.0f / Math.max(digSpeed, 1.0f)));
        int level = maid.getFavorabilityManager().getLevel();
        return Math.max(1, (int) (base / (1.0 + level * 0.25)));
    }

    /** 强制加载区块 (任务期间防卸载 + 梯级异步预加载).
     *  v79.62.1 修卡顿: setChunkForced 本身异步 (ChunkBuilder 线程生成, 主线程不阻塞) —
     *  对未生成区块也 force → 后台生成, 女仆到达时已 FULL, 不阻塞主线程.
     *  调用方必须限数量 (梯级: 当前 + 相邻预测区块, 每次几个) — 防一次性 1024 个排队卡 ChunkBuilder. */
    public static void forceChunk(ServerLevel world, int cx, int cz, boolean forced) {
        world.setChunkForced(cx, cz, forced);
    }
}
