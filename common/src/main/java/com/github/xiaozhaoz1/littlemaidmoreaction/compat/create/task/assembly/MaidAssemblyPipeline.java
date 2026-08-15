package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
//? if 1.20.1 {
import net.minecraftforge.items.ItemHandlerHelper;
//?} else {
import net.neoforged.neoforge.items.ItemHandlerHelper;
//?}

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 便携装配 Pipeline (v62: pipelineData 管理临时进度, Inventory 用独立 key).
 *
 * <p>材料来源 (三级): 背包 → 附近容器 → 隙间
 * <p>产物分发 (三级): 槽位 → 背包 → 隙间 → 地上
 */
public final class MaidAssemblyPipeline extends TaskStateMachine<MaidAssemblyPipeline.State> implements TaskConfigurable {

    enum State { IDLE, TRY_START, ADVANCE, STRIKE, EAT_RESET }

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.IDLE; }
    @Override public String taskType() { return "maid_assembly"; }
    @Override public boolean enableWorkEat() { return true; }

    @Override
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return new MaidAssemblyNetwork.MaidAssemblyMenuProvider(maid);
    }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.IDLE,       Set.of(State.TRY_START),
            State.TRY_START,  Set.of(State.ADVANCE, State.IDLE),
            State.ADVANCE,    Set.of(State.STRIKE, State.EAT_RESET, State.IDLE),
            State.STRIKE,     Set.of(State.ADVANCE),
            State.EAT_RESET,  Set.of(State.IDLE)
        );
    }

    @javax.annotation.Nullable
    @Override
    public java.util.function.Predicate<ItemStack> collectFilter(EntityMaid maid) {
        var inv = MaidAssemblyInventory.of(maid);
        ItemStack lock = inv.getMaterialLock();
        ItemStack mat = inv.getStackInSlot(8);
        return st -> itemIsFood(st)
            || (!lock.isEmpty() && ItemStackHelper.isSameItem(st, lock))
            || (!mat.isEmpty() && ItemStackHelper.isSameItem(st, mat));
    }

    /** create 运行时门控双平台 */
    private static boolean isCreateLoaded() {
//? if 1.20.1 {
        return net.minecraftforge.fml.ModList.get().isLoaded("create");
//?} else {
        return net.neoforged.fml.ModList.get().isLoaded("create");
//?}
    }

    @Override public List<TaskStep> steps() {
        return List.of(new TaskStep("assemble", "装配加工", StepType.INTERACT, List.of()));
    }

    @Override public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        if (!isCreateLoaded())
            return PipelineResult.failed("需要机械动力模组");
        return PipelineResult.ok("");
    }

    // executor/execute 删除 (v79.45) — 执行全归 GMPM tick 驱动

    // ── 状态业务逻辑 ──

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        if (TaskKeys.STATE_CANCELLED.equals(FlowTaskData.getState(maid))) {
            return null;
        }
        return switch (s) {
            case IDLE      -> tickIdle(maid);
            case TRY_START -> tickTryStart(maid);
            case ADVANCE   -> tickAdvance(world, maid);
            case STRIKE    -> tickStrike(world, maid);
            case EAT_RESET -> tickEatReset(maid);
        };
    }

    // ── IDLE: 冷却 → TRY_START ──

    private State tickIdle(EntityMaid maid) {
        CompoundTag pd = pipelineData(maid);
        int cd = pd.getInt("TryCd");
        if (cd > 0) { pd.putInt("TryCd", cd - 1); return State.IDLE; }
        pd.putInt("TryCd", 20);
        return State.TRY_START;
    }

    // ── TRY_START: 检查机器+食物+输入 ──

    private State tickTryStart(EntityMaid maid) {
        MaidAssemblyInventory inv = MaidAssemblyInventory.of(maid);
        if (!inv.hasAnyMachine()) return State.IDLE;
        if (!hasFood(maid)) return State.IDLE;
        ItemStack input = getCurrentInput(inv);
        if (input.isEmpty()) return State.IDLE;

        CompoundTag pd = pipelineData(maid);
        pd.putBoolean("InProc", true);
        pd.putInt("Slot", 0);
        pd.putInt("Pass", 0);
        pd.putInt("Timer", -1);
        return State.ADVANCE;
    }

    // ── ADVANCE: 推进槽位+匹配配方 ──

    private State tickAdvance(ServerLevel world, EntityMaid maid) {
        CompoundTag pd = pipelineData(maid);
        int advCd = pd.getInt("AdvCd");
        if (advCd > 0) { pd.putInt("AdvCd", advCd - 1); return State.ADVANCE; }
        pd.putInt("AdvCd", 5);

        MaidAssemblyInventory inv = MaidAssemblyInventory.of(maid);
        int pass = pd.getInt("Pass");

        while (pass < MaidAssemblyInventory.MACHINE_SLOTS) {
            MaidAssemblyService.MachineKind kind = inv.getMachineKind(pass);
            if (kind != null) {
                ItemStack input = getCurrentInput(inv);
                if (!input.isEmpty()) {
                    int dur = matchRecipe(world, maid, kind, input);
                    if (dur > 0) {
                        pd.putInt("Slot", pass);
                        pd.putInt("Pass", pass);
                        pd.putInt("Timer", dur);
                        return State.STRIKE;
                    }
                }
            }
            pass++;
        }

        ItemStack inter = inv.getStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT);
        if (!inter.isEmpty() && !MaidAssemblyService.isAssemblyIntermediate(inter)) {
            ItemStack remaining = inv.tryInsertOutput(inter.copy());
            inv.setStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT, ItemStack.EMPTY);
            if (!remaining.isEmpty()) deposit(maid, remaining);
            inv.saveToNBT();
        }
        pd.putInt("Pass", pass);
        return (pass >= MaidAssemblyInventory.MACHINE_SLOTS) ? State.EAT_RESET : State.ADVANCE;
    }

    // ── STRIKE: 计时器倒计时→执行配方 ──

    private State tickStrike(ServerLevel world, EntityMaid maid) {
        CompoundTag pd = pipelineData(maid);
        int timer = pd.getInt("Timer");
        if (timer > 0) { pd.putInt("Timer", timer - 1); return State.STRIKE; }

        MaidAssemblyInventory inv = MaidAssemblyInventory.of(maid);
        int slot = pd.getInt("Slot");
        int pass = pd.getInt("Pass");
        MaidAssemblyService.MachineKind kind = inv.getMachineKind(slot);

        ItemStack machineBlock = MaidAssemblyService.getMachineBlockStack(kind);
        ItemStack oldMainHand = maid.getMainHandItem().copy();
        if (!machineBlock.isEmpty()) maid.setItemInHand(InteractionHand.MAIN_HAND, machineBlock);

        MaidAssemblyService.playMachineSound(world, maid.blockPosition(),
            kind != null ? kind : MaidAssemblyService.MachineKind.PRESS);
        maid.swing(InteractionHand.MAIN_HAND);
        maid.setItemInHand(InteractionHand.MAIN_HAND, oldMainHand);

        if (kind != null) {
            ItemStack input = getCurrentInput(inv);
            if (!input.isEmpty()) {
                List<ItemStack> outputs = executeRecipe(world, maid, kind, input.copyWithCount(1));
                if (!outputs.isEmpty()) {
                    consumeInput(maid, inv, input);
                    boolean isLastPass = (pass + 1 >= MaidAssemblyInventory.MACHINE_SLOTS);
                    for (ItemStack out : outputs) {
                        if (out.isEmpty()) continue;
                        if (MaidAssemblyService.isAssemblyIntermediate(out)) {
                            ItemStack r = insertOrStack(inv, MaidAssemblyInventory.INTERMEDIATE_SLOT, out);
                            deposit(maid, r);
                        } else if (!isLastPass) {
                            ItemStack r = out;
                            if (inv.getStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT).isEmpty())
                                r = insertOrStack(inv, MaidAssemblyInventory.INTERMEDIATE_SLOT, out);
                            if (!r.isEmpty()) { r = inv.tryInsertOutput(r); deposit(maid, r); }
                        } else {
                            ItemStack r = inv.tryInsertOutput(out);
                            deposit(maid, r);
                        }
                    }
                }
            }
        }

        inv.autoRefillMaterial();
        inv.saveToNBT();
        pd.putInt("Timer", -1);
        pd.putInt("Pass", pass + 1);
        return State.ADVANCE;
    }

    // ── EAT_RESET: 保存库存 → 重置 → IDLE ──

    private State tickEatReset(EntityMaid maid) {
        MaidAssemblyInventory.of(maid).saveToNBT();
        CompoundTag pd = pipelineData(maid);
        pd.putBoolean("InProc", false);
        pd.remove("Timer"); pd.remove("Slot"); pd.remove("Pass");
        pd.remove("TryCd"); pd.remove("AdvCd");
        return State.IDLE;
    }

    // ── 工具方法 ──

    private boolean hasFood(EntityMaid maid) {
        ItemStack offhand = maid.getOffhandItem();
        if (!offhand.isEmpty() && itemIsFood(offhand)) return true;
        var bp = maid.getAvailableBackpackInv();
        for (int s = 0; s < bp.getSlots(); s++) {
            if (!bp.getStackInSlot(s).isEmpty() && itemIsFood(bp.getStackInSlot(s))) return true;
        }
        return false;
    }

    /** 可食判断双平台 (1.21 无 ItemStack.isEdible) */
    private static boolean itemIsFood(ItemStack stack) {
//? if 1.20.1 {
        return stack.getItem().isEdible();
//?} else {
        return stack.has(net.minecraft.core.component.DataComponents.FOOD);
//?}
    }

    /** 物品相同判断双平台 (1.21: isSameItemSameTags→isSameItemSameComponents) */
    private ItemStack getCurrentInput(MaidAssemblyInventory inv) {
        ItemStack inter = inv.getStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT);
        if (!inter.isEmpty()) return inter;
        return inv.getStackInSlot(MaidAssemblyInventory.MATERIAL_SLOT);
    }

    private int matchRecipe(ServerLevel world, EntityMaid maid,
                            MaidAssemblyService.MachineKind kind, ItemStack input) {
        if (input.isEmpty()) return 0;
        return switch (kind) {
            case PRESS, SAW, MILLSTONE -> {
                Recipe<?> r = MaidAssemblyService.findSimpleRecipe(world, kind, input);
                yield r != null ? MaidAssemblyService.getDuration(kind, maid) : 0;
            }
            case DEPLOYER -> {
                List<ItemStack> avail = MaidAssemblyService.collectAvailableItems(maid, true);
                ItemStack held = MaidAssemblyService.findDeployerHeldItem(world, input, avail);
                yield held != null ? MaidAssemblyService.getDuration(kind, maid) : 0;
            }
        };
    }

    private List<ItemStack> executeRecipe(ServerLevel world, EntityMaid maid,
                                           MaidAssemblyService.MachineKind kind, ItemStack input) {
        try {
            return switch (kind) {
                case PRESS, SAW, MILLSTONE -> {
                    Recipe<?> r = MaidAssemblyService.findSimpleRecipe(world, kind, input);
                    yield r != null ? MaidAssemblyService.processSimple(world, r, input) : List.of();
                }
                case DEPLOYER -> {
                    List<ItemStack> avail = MaidAssemblyService.collectAvailableItems(maid, true);
                    ItemStack held = MaidAssemblyService.findDeployerHeldItem(world, input, avail);
                    if (held == null) yield List.of();
                    DeployerApplicationRecipe r = MaidAssemblyService.findDeployingRecipe(world, input, held);
                    if (r == null) yield List.of();
                    consumeFromBackpack(maid, held);
                    yield MaidAssemblyService.processDeploying(world, r, input, held);
                }
            };
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.error("[MaidAssembly] executeRecipe failed kind={}", kind, e);
            return List.of();
        }
    }

    private void consumeInput(EntityMaid maid, MaidAssemblyInventory inv, ItemStack input) {
        ItemStack inter = inv.getStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT);
        if (!inter.isEmpty() && ItemStackHelper.isSameItem(inter, input)) {
            inter.shrink(1);
            inv.setStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT, inter);
        } else {
            ItemStack mat = inv.getStackInSlot(MaidAssemblyInventory.MATERIAL_SLOT);
            if (!mat.isEmpty()) { mat.shrink(1); inv.setStackInSlot(MaidAssemblyInventory.MATERIAL_SLOT, mat); }
        }
    }

    private ItemStack insertOrStack(MaidAssemblyInventory inv, int slot, ItemStack stack) {
        ItemStack ex = inv.getStackInSlot(slot);
        if (ex.isEmpty()) { inv.setStackInSlot(slot, stack.copy()); return ItemStack.EMPTY; }
        if (ItemStackHelper.isSameItem(ex, stack) && ex.getCount() + stack.getCount() <= ex.getMaxStackSize()) {
            ex.grow(stack.getCount()); inv.setStackInSlot(slot, ex); return ItemStack.EMPTY;
        }
        return stack;
    }

    private void consumeFromBackpack(EntityMaid maid, ItemStack target) {
        var bp = maid.getAvailableBackpackInv();
        for (int s = 0; s < bp.getSlots(); s++) {
            if (ItemStackHelper.isSameItem(bp.getStackInSlot(s), target)) {
                bp.extractItem(s, 1, false); return;
            }
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.NearbyContainerService.extractItem(
            maid.level(), maid.blockPosition(), MaidAssemblyService.SEARCH_RADIUS,
            st -> ItemStackHelper.isSameItem(st, target), java.util.Set.of(), true, maid);
    }

    private void deposit(EntityMaid maid, ItemStack stack) {
        if (stack.isEmpty()) return;
        stack = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), stack, false);
        if (!stack.isEmpty()) {
            var wireless = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.container.WirelessChestSpace.getWirelessHandler(maid);
            if (wireless != null) stack = ItemHandlerHelper.insertItemStacked(wireless, stack, false);
        }
        if (!stack.isEmpty() && maid.level() instanceof ServerLevel sl) {
            net.minecraft.world.entity.item.ItemEntity ie =
                new net.minecraft.world.entity.item.ItemEntity(sl, maid.getX(), maid.getY() + 1, maid.getZ(), stack);
            ie.setDefaultPickUpDelay();
            sl.addFreshEntity(ie);
        }
    }
}
