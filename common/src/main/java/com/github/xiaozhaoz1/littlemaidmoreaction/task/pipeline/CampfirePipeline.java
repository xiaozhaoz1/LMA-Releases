package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;

import java.util.List;
import java.util.Optional;

/**
 * v79.62.1 篝火烤食物管线 (主动任务) — 女仆找到篝火, 放入可烤生食, 捡起烤熟的掉落.
 *
 * <p>篝火与原版熔炉不同: {@link CampfireBlockEntity} 不是 Container (4 槽独立烹饪计时),
 * 烤熟经 {@code cookTick} 直接 {@code dropItemStack} 掉到世界 (不进背包).
 * 故女仆动作: ① 4 槽有空位 → 从背包找可烤食物 (getCookableRecipe 非空) → placeFood 放入;
 * ② 每 tick 扫描篝火周围 ItemEntity → maid.pickupItem 捡进背包.
 *
 * <p>固定工作点 (工作站式 gate 复用), 节拍 30t; 可烤判定走配方类型 CAMPFIRE_COOKING.
 */
public final class CampfirePipeline implements TaskPipeline {

    private static final int SCAN_RADIUS = 3;
    private static final int COOK_TIME = 600;   // 篝火默认烹饪时长 (30s, 原版 CampfireBlock 标准)

    @Override public String taskType() { return "campfire"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean workPointTask() { return true; }
    @Override public int executeInterval() { return 30; }

    @Override
    public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) {
        return w.getBlockEntity(p) instanceof CampfireBlockEntity;
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        // v79.62.2 不再依赖 TLM TARGET_POS (searchForDestination BFS 可能判不可达 → 永不设目标 →
        // 永远"没有篝火"): 就近扫描找篝火 (仿 SmithingPipeline.findSmithingTable, TLM 导航到 TARGET_POS
        // 由 tick 标定). 有篝火 且 (有可烤食物 或 篝火有烹饪物) 才通过
        BlockPos target = findCampfire(level, maid);
        if (target == null || !(level.getBlockEntity(target) instanceof CampfireBlockEntity)) {
            return PipelineResult.failed("附近没有篝火");
        }
        return PipelineResult.ok("");
    }

    /** 就近找篝火 (扫描女仆周围 ±4 格, 仿 SmithingPipeline.findSmithingTable) */
    public static BlockPos findCampfire(ServerLevel level, EntityMaid maid) {
        BlockPos center = maid.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (level.getBlockEntity(p) instanceof CampfireBlockEntity) return p.immutable();
        }
        return null;
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        // v79.62.2 自定目标: 就近找篝火 (不依赖 TLM searchForDestination BFS) —
        // 找到且 TARGET_POS 不是它 → 设 TARGET_POS + WALK_TARGET (TLM 导航走过去)
        BlockPos found = findCampfire(world, maid);
        if (found == null) {
            com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory.clearAllNav(maid);
            return;   // 无篝火 (会持续找)
        }
        var mem = maid.getBrain().getMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
        BlockPos cur = mem.isEmpty() ? null : mem.get().currentBlockPosition();
        if (cur == null || !cur.equals(found)) {
            maid.getBrain().setMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get(),
                    new net.minecraft.world.entity.ai.behavior.BlockPosTracker(found));
            net.minecraft.world.entity.ai.behavior.BehaviorUtils
                    .setWalkAndLookTargetMemories(maid, found, 1.0F, 0);
        }

        BlockPos target = com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.WorkStationPipeline
                .gate(world, maid, this);
        if (target == null) return;   // 未到达/节拍/目标失效

        if (!(world.getBlockEntity(target) instanceof CampfireBlockEntity campfire)) return;

        // ① 放可烤食物 (有空位 + 背包有生食)
        placeFood(world, maid, campfire);
        // ② 捡篝火周围熟食掉落
        pickCooked(world, maid, target);
    }

    /** 放一份可烤食物到篝火空位 (getCookableRecipe 判定) */
    private void placeFood(ServerLevel world, EntityMaid maid, CampfireBlockEntity campfire) {
        var items = campfire.getItems();
        // 有空位才放 (4 槽全满 → 跳过)
        boolean hasEmpty = false;
        for (ItemStack s : items) {
            if (s.isEmpty()) { hasEmpty = true; break; }
        }
        if (!hasEmpty) return;
        // 找背包可烤食物
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            // 可烤判定 (原版 campfire 配方)
            boolean cookable;
//? if 1.20.1 {
            cookable = campfire.getCookableRecipe(s).isPresent();
//?} else {
            cookable = campfire.getCookableRecipe(s).isPresent();
//?}
            if (!cookable) continue;
            if (campfire.placeFood(maid, s, COOK_TIME)) {
                return;   // 放了一份
            }
        }
    }

    /** 捡篝火周围掉落物 (烤熟的生食掉落) 进女仆背包 */
    private void pickCooked(ServerLevel world, EntityMaid maid, BlockPos campPos) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(campPos).inflate(SCAN_RADIUS);
        List<ItemEntity> drops = world.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay());
        for (ItemEntity drop : drops) {
            if (maid.distanceToSqr(drop) <= VanillaConstants.MINE_DIG_DIST_SQR) {
                maid.pickupItem(drop, false);
            }
        }
    }

    /** 当前目标篝火 pos (TARGET_POS 记忆) */
    private static BlockPos targetPos(EntityMaid maid) {
        var mem = maid.getBrain().getMemory(InitEntities.TARGET_POS.get());
        return mem.isEmpty() ? null : mem.get().currentBlockPosition();
    }

    @Override
    public void onCleanup(EntityMaid maid) {}
}
