package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskMemory;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlock;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlockEntity;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 女仆跑步发电管线 v4.6 — 脚下检测 + 直接BE注入 + 冷却防重转。
 */
public final class RunningBeltPipeline implements TaskPipeline {

    private static final String KEY_TARGET = "lma_running_belt_target";
    private static final String KEY_CONVERTED = "lma_running_belt_converted";
    private static final String KEY_IDLE = "lma_running_belt_idle";
    private static final String KEY_FOOD_TIMER = "lma_running_belt_food_timer";
    private static final String KEY_COOLDOWN = "lma_running_belt_cooldown";
    private static final int IDLE_TIMEOUT = 100;
    private static final int FOOD_INTERVAL = 100;
    private static final int COOLDOWN_TICKS = 60; // 3秒冷却防重转
    private static final float SPRINT_SPEED = 0.2f; // ~96 RPM

    @Override public String taskType() { return "running_belt"; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("run", "跑步发电", StepType.INTERACT, List.of())); }
    @Override public PipelineResult execute(ServerLevel l, EntityMaid m, PipelineContext c) { return PipelineResult.ok(""); }
    @Override public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) { return PipelineResult.ok(""); }

    public static IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) { tick(w, m); return TaskResult.CONTINUE; }
            @Override public void onStop(EntityMaid maid) { cleanup(maid); }
        };
    }

    // ── Tick ──

    public static void tick(ServerLevel world, EntityMaid maid) {
        var d = maid.getPersistentData();
        if ("true".equals(d.getString(KEY_CONVERTED))) {
            tickRunning(world, maid, d);
        } else {
            tickSearching(world, maid, d);
        }
    }

    // ── 搜索: 脚下检测 (v4.6 无寻路) ──

    private static void tickSearching(ServerLevel world, EntityMaid maid, CompoundTag d) {
        // 冷却中 → 倒数
        int cd = d.getInt(KEY_COOLDOWN);
        if (cd > 0) { d.putInt(KEY_COOLDOWN, cd - 1); return; }

        // 检测脚下和脚下一格
        BlockPos maidPos = maid.blockPosition();
        BlockPos beltPos = null;
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos p = maidPos.offset(0, dy, 0);
            BlockState s = world.getBlockState(p);
            if (isHorizontalBelt(s)) { beltPos = p.immutable(); break; }
        }
        if (beltPos == null) return;

        // 转换前检查食物 (v4.6.1: 无食物不转)
        if (RunningBeltService.findFoodItem(maid) == null) return;

        if (RunningBeltService.convertToMaidPowerBelt(world, beltPos)) {
            d.putString(KEY_TARGET, beltPos.toShortString());
            d.putString(KEY_CONVERTED, "true");
            d.putInt(KEY_IDLE, 0);
            d.putInt(KEY_FOOD_TIMER, 0);
            d.putInt(KEY_COOLDOWN, 0);
        }
    }

    // ── 跑步: 直调BE注入 + 食物 + 空闲检测 ──

    private static void tickRunning(ServerLevel world, EntityMaid maid, CompoundTag d) {
        BlockPos target = readPos(d, KEY_TARGET);
        if (target == null) { revertAndClear(world, maid, d); return; }

        boolean onBelt = RunningBeltService.isMaidOnBelt(maid, target);
        if (!onBelt) {
            int idle = d.getInt(KEY_IDLE) + 1;
            d.putInt(KEY_IDLE, idle);
            if (idle >= IDLE_TIMEOUT) { revertAndClear(world, maid, d); }
            return;
        }

        // v4.6.1: 先查食物→无食物直接停(不先跑再停)
        int foodTimer = d.getInt(KEY_FOOD_TIMER) + 1;
        if (foodTimer >= FOOD_INTERVAL) {
            var food = RunningBeltService.findFoodItem(maid);
            if (food == null) { revertAndClear(world, maid, d); return; }
            RunningBeltService.consumeFood(maid, food.slotIndex());
            foodTimer = 0;
        }
        d.putInt(KEY_FOOD_TIMER, foodTimer);

        // 有食物→跑步
        d.putInt(KEY_IDLE, 0);
        maid.setSprinting(true);
        MaidPowerBeltBlockEntity be = MaidPowerBeltBlock.getControllerBE(world, target);
        if (be != null) be.addSurfaceMovement(SPRINT_SPEED);
    }

    // ── 清理 ──

    public static void cleanup(EntityMaid maid) {
        maid.setSprinting(false);
        if (!(maid.level() instanceof ServerLevel world)) return;
        revertAndClear(world, maid, maid.getPersistentData());
        LmaTaskMemory.clearAllNav(maid);
    }

    private static void revertAndClear(ServerLevel world, EntityMaid maid, CompoundTag d) {
        maid.setSprinting(false);
        BlockPos target = readPos(d, KEY_TARGET);
        if (target != null) RunningBeltService.revertToRegularBelt(world, target);
        d.remove(KEY_TARGET); d.remove(KEY_CONVERTED); d.remove(KEY_IDLE);
        d.remove(KEY_FOOD_TIMER);
        d.putInt(KEY_COOLDOWN, COOLDOWN_TICKS); // v4.6: 冷却防重转
    }

    // ── 工具 ──

    private static BlockPos readPos(CompoundTag d, String key) {
        String s = d.getString(key);
        if (s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) { return null; }
    }

    private static boolean isHorizontalBelt(BlockState state) {
        return state.getBlock() instanceof BeltBlock
                && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
    }
}
