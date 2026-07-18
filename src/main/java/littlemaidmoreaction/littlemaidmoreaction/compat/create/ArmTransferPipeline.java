package littlemaidmoreaction.littlemaidmoreaction.compat.create;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskMemory;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.service.TaskStateService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 女仆搬运管线 — 两状态 TAKE ↔ DEPOSIT，无限循环。
 *
 * <p>TAKE:  走到取出点 → 提取物品 → 记录物品ID → DEPOSIT
 * <br>DEPOSIT: 走到放入点 → 放入物品 → TAKE
 * <br>空源/目标满: 无限等待（不超时，永不完成）
 */
public final class ArmTransferPipeline implements TaskPipeline {

    static final String KEY_STATE = "lma_arm_state";
    static final String KEY_TAKE = "lma_arm_take";
    static final String KEY_DEPOSIT = "lma_arm_deposit";
    static final String KEY_ITEM = "lma_arm_item";

    enum State { TAKE, DEPOSIT }

    // ── TaskPipeline ──

    @Override public String taskType() { return "arm_transfer"; }
    @Override public PipelineResult execute(ServerLevel l, EntityMaid m, PipelineContext c) { return PipelineResult.ok(""); }
    @Override public List<TaskStep> steps() { return List.of(); }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        var d = maid.getPersistentData();
        if (!d.contains(KEY_TAKE) || !d.contains(KEY_DEPOSIT))
            return PipelineResult.failed("坐标未设置");
        return PipelineResult.ok("就绪");
    }

    /** TaskRegistry 使用的 executor — 仅当坐标已设置时驱动逻辑 */
    public static IExecutor executor() {
        return (world, maid, pos, data) -> {
            var d = maid.getPersistentData();
            if (!d.contains(KEY_TAKE) || !d.contains(KEY_DEPOSIT))
                return TaskResult.CONTINUE; // 等待木棍标记
            tick((ServerLevel) world, maid);
            return TaskResult.CONTINUE;
        };
    }

    // ── 主 tick ──

    public static void tick(ServerLevel world, EntityMaid maid) {
        var d = maid.getPersistentData();
        if (!d.contains(KEY_TAKE) || !d.contains(KEY_DEPOSIT)) return;
        BlockPos takePos = NbtUtils.readBlockPos(d.getCompound(KEY_TAKE));
        BlockPos depositPos = NbtUtils.readBlockPos(d.getCompound(KEY_DEPOSIT));
        State state = readState(d);

        if (state == State.TAKE) {
            tickTake(world, maid, takePos, depositPos);
        } else {
            tickDeposit(world, maid, takePos, depositPos);
        }
    }

    // ── TAKE ──

    private static void tickTake(ServerLevel world, EntityMaid maid, BlockPos takePos, BlockPos depositPos) {
        if (!arrived(maid, takePos)) {
            navigateTo(maid, takePos);
            return;
        }
        var item = ArmTransferService.readSourceItem(maid, takePos);
        if (item.isEmpty()) return;  // 空源 → 无限等待
        int count = ArmTransferService.computeExtractCount(maid, item);
        if (count <= 0) return;
        ArmTransferService.executeExtract(maid, takePos, item, count);
        maid.swing(InteractionHand.MAIN_HAND);
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
        maid.getPersistentData().putString(KEY_ITEM, id != null ? id.toString() : item.getDescriptionId());
        setState(maid, State.DEPOSIT);
    }

    // ── DEPOSIT ──

    private static void tickDeposit(ServerLevel world, EntityMaid maid, BlockPos takePos, BlockPos depositPos) {
        if (!arrived(maid, depositPos)) {
            navigateTo(maid, depositPos);
            return;
        }
        String itemId = maid.getPersistentData().getString(KEY_ITEM);
        var mItem = itemId.isEmpty() ? ArmTransferService.readMaidItem(maid) : findMaidItem(maid, itemId);
        if (mItem.isEmpty()) { setState(maid, State.TAKE); return; }
        int count = ArmTransferService.computeDepositCount(maid, depositPos, mItem);
        if (count <= 0) return;  // 目标满 → 无限等待
        ArmTransferService.executeDeposit(maid, depositPos, mItem, count);
        maid.swing(InteractionHand.MAIN_HAND);
        setState(maid, State.TAKE);
    }

    // ── 导航 ──

    private static void navigateTo(EntityMaid maid, BlockPos target) {
        LmaTaskMemory.setNavTarget(maid, target);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
    }

    // ── 清理 ──

    static void cleanup(EntityMaid maid) {
        var d = maid.getPersistentData();
        d.remove(KEY_STATE); d.remove(KEY_TAKE); d.remove(KEY_DEPOSIT);
        d.remove(KEY_ITEM); d.remove("lma_arm_wait");
        d.remove("lma_flow_task"); d.remove("lma_flow_state");
        LmaTaskMemory.clearAllNav(maid);
    }

    // ── 工具 ──

    private static boolean arrived(EntityMaid m, BlockPos p) { return p.distToCenterSqr(m.position()) < 9.0; }

    private static State readState(net.minecraft.nbt.CompoundTag d) {
        try { return State.valueOf(d.getString(KEY_STATE)); } catch (Exception e) { return State.TAKE; }
    }
    private static void setState(EntityMaid m, State s) { m.getPersistentData().putString(KEY_STATE, s.name()); }

    private static ItemStack findMaidItem(EntityMaid maid, String itemId) {
        var rl = ResourceLocation.tryParse(itemId); if (rl == null) return ItemStack.EMPTY;
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
