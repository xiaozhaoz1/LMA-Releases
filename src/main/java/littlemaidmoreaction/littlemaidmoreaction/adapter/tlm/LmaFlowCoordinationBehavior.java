package littlemaidmoreaction.littlemaidmoreaction.adapter.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.VanillaTasks;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.core.memory.LmaTaskMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** v22: 薄化 — 只做状态机编排, 任务执行委托 VanillaTasks */
public final class LmaFlowCoordinationBehavior extends MaidCheckRateTask {

    private static final int CHECK_INTERVAL = 100;
    private static final int NAV_TIMEOUT_TICKS = 600;
    private static final double ARRIVE_DIST_SQR = 9.0;

    public LmaFlowCoordinationBehavior() {
        super(ImmutableMap.of());
        this.setMaxCheckRate(CHECK_INTERVAL);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, EntityMaid maid) {
        if (!super.checkExtraStartConditions(world, maid)) return false;

        // 1. 先检查 PersistentData (AI StartTaskTool 路径)
        String task = LmaFlowTask.getCurrentFlowTaskType(maid);
        if (!task.isEmpty() && !"none".equals(task)) {
            return "in_progress".equals(maid.getPersistentData().getString("lma_flow_state"));
        }

        // 2. Fallback: GUI 手动分配路径 — 从 Brain 当前任务提取类型并初始化 PersistentData
        var maidTask = maid.getTask();
        if (!LmaFlowTask.isLmaTask(maidTask)) return false;
        String taskType = LmaTaskTypeRegistry.extractTaskType(maidTask.getUid().getPath());
        if (taskType == null) return false;

        CompoundTag data = maid.getPersistentData();
        data.putString("lma_flow_task", taskType);
        data.putString("lma_flow_state", "in_progress");
        data.putLong("lma_flow_tick", world.getGameTime());
        LittleMaidMoreAction.LOGGER.debug("[V28] GUI-init task '{}' — initialized PersistentData", taskType);
        return true;
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTime) {
        CompoundTag data = maid.getPersistentData();
        String taskType = data.getString("lma_flow_task");
        if (taskType.isEmpty()) return;

        BlockPos navTarget = LmaTaskMemory.getNavTarget(maid);
        if (navTarget != null) {
            if (gameTime - LmaTaskMemory.getNavStartTick(maid) > NAV_TIMEOUT_TICKS) {
                LmaTaskMemory.clearAllNav(maid);
            } else if (isBlockValid(world, navTarget, taskType)) {
                if (navTarget.distSqr(maid.blockPosition()) < ARRIVE_DIST_SQR) {
                    LmaTaskMemory.clearAllNav(maid);
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    executeInteract(world, maid, navTarget, taskType);
                    return;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(maid, navTarget, 1.0F, 2);
                return;
            } else {
                LmaTaskMemory.clearAllNav(maid);
            }
        }

        BlockPos nearest = searchTargetBlock(world, maid, taskType);
        if (nearest == null) {
            failTask(maid, "找不到" + taskType + "方块");
            return;
        }
        if (nearest.distSqr(maid.blockPosition()) < ARRIVE_DIST_SQR) {
            executeInteract(world, maid, nearest, taskType);
            return;
        }
        LmaTaskMemory.setNavTarget(maid, nearest);
        LmaTaskMemory.setNavStartTick(maid, gameTime);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, nearest, 1.0F, 2);
    }

    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        BlockPos navTarget = LmaTaskMemory.getNavTarget(maid);
        if (navTarget == null) return;
        if (navTarget.distSqr(maid.blockPosition()) < ARRIVE_DIST_SQR) {
            if ("in_progress".equals(maid.getPersistentData().getString("lma_flow_state"))) {
                LmaTaskMemory.clearAllNav(maid);
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                executeInteract(world, maid, navTarget, maid.getPersistentData().getString("lma_flow_task"));
            }
        }
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void executeInteract(ServerLevel world, EntityMaid maid, BlockPos pos, String taskType) {
        CompoundTag data = maid.getPersistentData();
        String target = data.getString("lma_task_target");
        String input = data.getString("lma_task_input");

        switch (taskType) {
            case "craft_chain" -> { if (VanillaTasks.craft(world, maid, pos, target)) completeTask(maid); else failTask(maid, "无法合成"); }
            case "furnace"     -> { VanillaTasks.furnace(world, maid, pos, input); completeTask(maid); }
            case "jukebox"     -> { VanillaTasks.jukebox(world, maid, pos, target); }
            case "bell_ring"   -> { VanillaTasks.bell(world, maid, pos); completeTask(maid); }
            case "altar_craft" -> doAltarCraft(world, maid, pos, target);
        }
    }

    private void doAltarCraft(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        var action = new littlemaidmoreaction.littlemaidmoreaction.impl.action.world.PlaceAltarItemAction();
        Map<String, String> params = new HashMap<>();
        params.put("item_id", target.isEmpty() ? "minecraft:coal" : target);
        params.put("range", String.valueOf((int) maid.getRestrictRadius()));
        action.execute(new littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext(maid), params);
        completeTask(maid);
    }

    private static BlockPos searchTargetBlock(ServerLevel world, EntityMaid maid, String taskType) {
        String blockId = switch (taskType) {
            case "craft_chain" -> "minecraft:crafting_table";
            case "furnace" -> "minecraft:furnace";
            case "jukebox" -> "minecraft:jukebox";
            case "bell_ring" -> "minecraft:bell";
            case "altar_craft" -> "touhou_little_maid:altar";
            default -> null;
        };
        if (blockId == null) return null;
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(blockId));
        if (block == null) return null;
        var matches = BlockSearch.findBlocks(world, maid.blockPosition(), (int) maid.getRestrictRadius(), 4, (p, s) -> s.is(block));
        return matches.isEmpty() ? null : matches.get(0).pos();
    }

    private static boolean isBlockValid(ServerLevel world, BlockPos pos, String taskType) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        return switch (taskType) {
            case "craft_chain" -> state.is(Blocks.CRAFTING_TABLE);
            case "furnace" -> state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER);
            case "jukebox" -> state.is(Blocks.JUKEBOX);
            case "bell_ring" -> state.getBlock() instanceof BellBlock;
            case "altar_craft" -> world.getBlockEntity(pos) instanceof com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
            default -> false;
        };
    }

    static void completeTask(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.putString("lma_flow_state", "completed");
        data.putString("lma_task_completed", data.getString("lma_flow_task"));
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        data.remove("lma_flow_cached");
    }

    static void failTask(EntityMaid maid, String reason) {
        CompoundTag data = maid.getPersistentData();
        data.putString("lma_flow_state", "failed");
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        data.remove("lma_flow_cached");
        data.putString("lma_fail_reason", reason);
        LittleMaidMoreAction.LOGGER.warn("[V18] task '{}' failed: {}", data.getString("lma_flow_task"), reason);
    }

    @Deprecated
    static void clearNavData(CompoundTag data) {
        data.remove("lma_nav_tx"); data.remove("lma_nav_ty"); data.remove("lma_nav_tz");
        data.remove("lma_nav_block"); data.remove("lma_nav_tick");
    }
}
