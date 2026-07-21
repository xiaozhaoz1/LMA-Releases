package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.navigation.NavigationMemory;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskStateMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 女仆搬运管线 (v46 迁移至 TaskStateMachine).
 *
 * <p>四状态循环:
 * <pre>
 * TO_TAKE → TAKING → TO_DEPOSIT → DEPOSITING → TO_TAKE → ...
 * </pre>
 *
 * <p>TO_TAKE/TO_DEPOSIT: 导航阶段。到达后进入对应执行状态。
 * <br>TAKING/DEPOSITING: 执行阶段。成功后推进，空源/目标满时原地等待。
 */
public final class ArmTransferPipeline extends TaskStateMachine<ArmTransferPipeline.State> {

    enum State { TO_TAKE, TAKING, TO_DEPOSIT, DEPOSITING }

    static final String KEY_TAKE = "lma_arm_take";
    static final String KEY_DEPOSIT = "lma_arm_deposit";
    static final String KEY_ITEM = "lma_arm_item";

    // ── 引擎必需 ──

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.TO_TAKE; }
    @Override public String taskType() { return "arm_transfer"; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.TO_TAKE,     Set.of(State.TAKING),
            State.TAKING,      Set.of(State.TO_DEPOSIT),
            State.TO_DEPOSIT,  Set.of(State.DEPOSITING),
            State.DEPOSITING,  Set.of(State.TO_TAKE)
        );
    }

    // ── 可选覆写 ──

    @Override
    public List<TaskStep> steps() {
        return List.of(new TaskStep("move", "搬运物品", StepType.INTERACT, List.of()));
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        var d = maid.getPersistentData();
        if (!d.contains(KEY_TAKE) || !d.contains(KEY_DEPOSIT))
            return PipelineResult.failed("坐标未设置");
        return PipelineResult.ok("就绪");
    }

    @Override
    protected void onEnter(State state, ServerLevel world, EntityMaid maid) {
        if (state == State.TO_TAKE || state == State.TO_DEPOSIT) {
            NavigationMemory.clearAllNav(maid);
        }
    }

    @Override
    protected void cleanup(EntityMaid maid) {
        super.cleanup(maid);
        var d = maid.getPersistentData();
        // v52: KEY_TAKE/KEY_DEPOSIT 持久保留 — 玩家只设一次，重启/TLM重置后仍在
        d.remove(KEY_ITEM);
        d.remove("lma_arm_wait");
        NavigationMemory.clearAllNav(maid);
    }

    /**
     * 覆写 executor — 坐标未设置时跳过 tick (保持原行为)。
     */
    @Override
    public IExecutor executor() {
        return new IExecutor() {
            @Override
            public TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos, CompoundTag data) {
                if (!data.contains(KEY_TAKE) || !data.contains(KEY_DEPOSIT))
                    return TaskResult.CONTINUE;
                tick(world, maid);
                return TaskResult.CONTINUE;
            }

            @Override
            public void onStop(EntityMaid maid) {
                cleanup(maid);
            }
        };
    }

    // ── 状态业务逻辑 ──

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        var d = maid.getPersistentData();
        BlockPos takePos = NbtUtils.readBlockPos(d.getCompound(KEY_TAKE));
        BlockPos depositPos = NbtUtils.readBlockPos(d.getCompound(KEY_DEPOSIT));

        return switch (s) {
            case TO_TAKE -> {
                if (arrived(maid, takePos)) yield State.TAKING;
                navigateTo(maid, takePos);
                yield null;
            }
            case TAKING -> {
                var item = ArmTransferService.readSourceItem(maid, takePos);
                if (item.isEmpty()) { yield null; } // 空源 → 等待
                int count = ArmTransferService.computeExtractCount(maid, item);
                if (count <= 0) { yield null; }
                ArmTransferService.executeExtract(maid, takePos, item, count);
                if (world.getGameTime() % 20 == 0) maid.swing(InteractionHand.MAIN_HAND);
                ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
                maid.getPersistentData().putString(KEY_ITEM, id != null ? id.toString() : item.getDescriptionId());
                yield State.TO_DEPOSIT;
            }
            case TO_DEPOSIT -> {
                if (arrived(maid, depositPos)) yield State.DEPOSITING;
                navigateTo(maid, depositPos);
                yield null;
            }
            case DEPOSITING -> {
                String itemId = maid.getPersistentData().getString(KEY_ITEM);
                var mItem = itemId.isEmpty()
                    ? ArmTransferService.readMaidItem(maid)
                    : findMaidItem(maid, itemId);
                if (mItem.isEmpty()) { yield State.TO_TAKE; } // 无物品 → 回去取
                int count = ArmTransferService.computeDepositCount(maid, depositPos, mItem);
                if (count <= 0) { yield null; } // 目标满 → 等待
                ArmTransferService.executeDeposit(maid, depositPos, mItem, count);
                if (world.getGameTime() % 20 == 0) maid.swing(InteractionHand.MAIN_HAND);
                yield State.TO_TAKE;
            }
        };
    }

    // ── 辅助 ──

    private static void navigateTo(EntityMaid maid, BlockPos target) {
        NavigationMemory.setNavTarget(maid, target);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
    }

    private static boolean arrived(EntityMaid m, BlockPos p) {
        return p.distToCenterSqr(m.position()) < 9.0;
    }

    private static ItemStack findMaidItem(EntityMaid maid, String itemId) {
        var rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return ItemStack.EMPTY;
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
            if (rl.equals(id)) return s.copy();
        }
        return ItemStack.EMPTY;
    }
}
