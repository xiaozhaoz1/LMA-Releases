package littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * 便携装配 Pipeline — 状态机: IDLE→ADVANCE→STRIKE→EAT/RESET.
 *
 * <p>存储在 maid.getPersistentData() → "maid_assembly":
 * <pre>
 *   InProc (bool) — 装配进行中
 *   Slot   (int)  — 当前机器槽位
 *   Timer  (int)  — 剩余加工tick (>0=加工中, 0=完成, -1=前进)
 *   Pass   (int)  — 本轮已处理槽数 (0..7, 第8次=pause eat)
 * </pre>
 *
 * <p>原料来源: 中间产物槽(优先) → 原材料槽
 * <p>食物: 副手或背包中有食物才能启动
 */
public final class MaidAssemblyPipeline implements TaskPipeline {

    private static final String KEY = "maid_assembly";

    @Override public String taskType() { return "maid_assembly"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean needsGameTick() { return true; }
    @Override public boolean enableWorkEat() { return true; }

    @javax.annotation.Nullable
    @Override public java.util.function.Predicate<ItemStack> collectFilter(EntityMaid maid) {
        var inv = MaidAssemblyInventory.of(maid);
        ItemStack lock = inv.getMaterialLock();
        ItemStack mat = inv.getStackInSlot(8);
        return st -> st.isEdible()
            || (!lock.isEmpty() && ItemStack.isSameItemSameTags(st, lock))
            || (!mat.isEmpty() && ItemStack.isSameItemSameTags(st, mat));
    }

    @Override public List<TaskStep> steps() {
        return List.of(new TaskStep("assemble", "装配加工", StepType.INTERACT, List.of()));
    }

    @Override public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("create"))
            return PipelineResult.failed("需要机械动力模组");
        return PipelineResult.ok("");
    }

    @Override public void onCleanup(EntityMaid maid) {
        CompoundTag r = maid.getPersistentData().getCompound(KEY);
        r.remove("InProc"); r.remove("Timer"); r.remove("Slot"); r.remove("Pass"); r.remove("TryCd"); r.remove("AdvCd");
        maid.getPersistentData().put(KEY, r);
    }

    @Override public void interrupt(EntityMaid maid) { onCleanup(maid); }

    public IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, net.minecraft.core.BlockPos p, CompoundTag d) {
                tick(w, m); return TaskResult.CONTINUE;
            }
            @Override public void onStop(EntityMaid m) { onCleanup(m); }
        };
    }

    // ═══════════════════════════════════════
    // Main tick entry
    // ═══════════════════════════════════════

    public void tick(ServerLevel world, EntityMaid maid) {
        CompoundTag root = maid.getPersistentData().getCompound(KEY);
        if (TaskKeys.STATE_CANCELLED.equals(maid.getPersistentData().getString(TaskKeys.FLOW_STATE))) {
            onCleanup(maid); return;
        }

        if (!root.getBoolean("InProc")) {
            int cd = root.getInt("TryCd");
            if (cd > 0) { root.putInt("TryCd", cd - 1); maid.getPersistentData().put(KEY, root); return; }
            root.putInt("TryCd", 20);
            tryStart(maid, root);
            return;
        }

        int pass = root.getInt("Pass");
        int timer = root.getInt("Timer");

        if (pass >= MaidAssemblyInventory.MACHINE_SLOTS) {
            eatAndReset(maid, root);
            return;
        }

        if (timer > 0) {
            root.putInt("Timer", timer - 1);
            maid.getPersistentData().put(KEY, root);
            return;
        }

        if (timer == 0) {
            strike(world, maid, root);
            return;
        }

        // timer < 0 → advance (5 tick cooldown between recipe checks)
        int advCd = root.getInt("AdvCd");
        if (advCd > 0) { root.putInt("AdvCd", advCd - 1); maid.getPersistentData().put(KEY, root); return; }
        root.putInt("AdvCd", 5);
        advanceSlot(world, maid, root);
    }

    // ═══════════════════════════════════════
    // Start
    // ═══════════════════════════════════════

    private void tryStart(EntityMaid maid, CompoundTag root) {
        MaidAssemblyInventory inv = MaidAssemblyInventory.of(maid);

        // 检查有机器方块
        if (!inv.hasAnyMachine()) return;

        // 检查有食物: 副手或背包
        if (!hasFood(maid)) return;

        // 检查有原料: 中间产物优先, 否则材料槽
        ItemStack input = getCurrentInput(inv);
        if (input.isEmpty()) return;

        root.putBoolean("InProc", true);
        root.putInt("Slot", 0);
        root.putInt("Pass", 0);
        root.putInt("Timer", -1);
        maid.getPersistentData().put(KEY, root);
    }

    /** 检查女仆副手或背包中是否有食物 */
    private boolean hasFood(EntityMaid maid) {
        ItemStack offhand = maid.getOffhandItem();
        if (!offhand.isEmpty() && offhand.isEdible()) return true;
        var bp = maid.getAvailableBackpackInv();
        for (int s = 0; s < bp.getSlots(); s++) {
            ItemStack st = bp.getStackInSlot(s);
            if (!st.isEmpty() && st.isEdible()) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════
    // Advance to next slot
    // ═══════════════════════════════════════

    private void advanceSlot(ServerLevel world, EntityMaid maid, CompoundTag root) {
        MaidAssemblyInventory inv = MaidAssemblyInventory.of(maid);
        int pass = root.getInt("Pass");

        while (pass < MaidAssemblyInventory.MACHINE_SLOTS) {
            MaidAssemblyService.MachineKind kind = inv.getMachineKind(pass);
            if (kind != null) {
                // 原料: 中间产物优先→材料槽
                ItemStack input = getCurrentInput(inv);
                if (!input.isEmpty()) {
                    int dur = matchRecipe(world, maid, kind, input);
                    if (dur > 0) {
                        root.putInt("Slot", pass);
                        root.putInt("Pass", pass);
                        root.putInt("Timer", dur);
                        maid.getPersistentData().put(KEY, root);
                        return;
                    }
                }
            }
            pass++;
            root.putInt("Pass", pass);
        }
        // 所有机器槽遍历完毕 → 非装配中间产物移到最终输出
        ItemStack inter = inv.getStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT);
        if (!inter.isEmpty() && !MaidAssemblyService.isAssemblyIntermediate(inter)) {
            ItemStack remaining = inv.tryInsertOutput(inter.copy());
            inv.setStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT, ItemStack.EMPTY);
            if (!remaining.isEmpty()) deposit(maid, remaining);
            inv.saveToNBT();
        }
        maid.getPersistentData().put(KEY, root);
    }

    // ═══════════════════════════════════════
    // Strike: execute recipe with work animation
    // ═══════════════════════════════════════

    private void strike(ServerLevel world, EntityMaid maid, CompoundTag root) {
        MaidAssemblyInventory inv = MaidAssemblyInventory.of(maid);
        int slot = root.getInt("Slot");
        int pass = root.getInt("Pass");
        MaidAssemblyService.MachineKind kind = inv.getMachineKind(slot);

        // 工作动画: 主手拿机器方块→摇摆→音效
        ItemStack machineBlock = MaidAssemblyService.getMachineBlockStack(kind);
        ItemStack oldMainHand = maid.getMainHandItem().copy();
        if (!machineBlock.isEmpty()) {
            maid.setItemInHand(InteractionHand.MAIN_HAND, machineBlock);
        }

        MaidAssemblyService.playMachineSound(world, maid.blockPosition(),
            kind != null ? kind : MaidAssemblyService.MachineKind.PRESS);
        maid.swing(InteractionHand.MAIN_HAND);

        // 恢复主手
        maid.setItemInHand(InteractionHand.MAIN_HAND, oldMainHand);

        if (kind == null) {
            advanceAfterStrike(maid, root, pass);
            return;
        }

        // 获取输入: 中间产物优先
        ItemStack input = getCurrentInput(inv);
        if (input.isEmpty()) {
            advanceAfterStrike(maid, root, pass);
            return;
        }

        ItemStack single = input.copyWithCount(1);
        List<ItemStack> outputs = executeRecipe(world, maid, kind, single);

        if (outputs.isEmpty()) {
            advanceAfterStrike(maid, root, pass);
            return;
        }

        // 消耗原料 (从中间槽或材料槽)
        consumeInput(maid, inv, input);

        // 分发产出
        boolean isLastPass = (pass + 1 >= MaidAssemblyInventory.MACHINE_SLOTS);
        for (ItemStack out : outputs) {
            if (out.isEmpty()) continue;
            if (MaidAssemblyService.isAssemblyIntermediate(out)) {
                // 装配中间产物 → 中间槽
                ItemStack r = insertOrStack(inv, MaidAssemblyInventory.INTERMEDIATE_SLOT, out);
                deposit(maid, r);
            } else if (!isLastPass) {
                // 前7轮: 第一个非中间产物→中间槽, 其余→输出
                ItemStack r = out;
                if (inv.getStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT).isEmpty())
                    r = insertOrStack(inv, MaidAssemblyInventory.INTERMEDIATE_SLOT, out);
                if (!r.isEmpty()) { r = inv.tryInsertOutput(r); deposit(maid, r); }
            } else {
                // 最后一轮 → 输出
                ItemStack r = inv.tryInsertOutput(out);
                deposit(maid, r);
            }
        }

        // 材料用完后自动补充
        inv.autoRefillMaterial();

        inv.saveToNBT();
        advanceAfterStrike(maid, root, pass);
    }

    private void advanceAfterStrike(EntityMaid maid, CompoundTag root, int pass) {
        root.putInt("Timer", -1);
        root.putInt("Pass", pass + 1);
        maid.getPersistentData().put(KEY, root);
    }

    // ═══════════════════════════════════════
    // Eat & Reset
    // ═══════════════════════════════════════

    private void eatAndReset(EntityMaid maid, CompoundTag root) {
        var inv = MaidAssemblyInventory.of(maid);
        inv.saveToNBT();
        root.putBoolean("InProc", false);
        root.remove("Timer"); root.remove("Slot"); root.remove("Pass"); root.remove("TryCd"); root.remove("AdvCd");
        maid.getPersistentData().put(KEY, root);
    }

    // ═══════════════════════════════════════
    // Recipe matching
    // ═══════════════════════════════════════

    private int matchRecipe(ServerLevel world, EntityMaid maid,
                            MaidAssemblyService.MachineKind kind, ItemStack input) {
        if (input.isEmpty()) return 0;
        ItemStack single = input.copyWithCount(1);

        return switch (kind) {
            case PRESS, SAW, MILLSTONE -> {
                Recipe<?> r = MaidAssemblyService.findSimpleRecipe(world, kind, single);
                yield r != null ? MaidAssemblyService.getDuration(kind, maid) : 0;
            }
            case DEPLOYER -> {
                List<ItemStack> avail = MaidAssemblyService.collectAvailableItems(maid);
                ItemStack held = MaidAssemblyService.findDeployerHeldItem(world, single, avail);
                yield held != null ? MaidAssemblyService.getDuration(kind, maid) : 0;
            }
        };
    }

    // ═══════════════════════════════════════
    // Recipe execution
    // ═══════════════════════════════════════

    private List<ItemStack> executeRecipe(ServerLevel world, EntityMaid maid,
                                           MaidAssemblyService.MachineKind kind, ItemStack input) {
        try {
            return switch (kind) {
                case PRESS, SAW, MILLSTONE -> {
                    Recipe<?> r = MaidAssemblyService.findSimpleRecipe(world, kind, input);
                    yield r != null ? MaidAssemblyService.processSimple(world, r, input) : List.of();
                }
                case DEPLOYER -> {
                    List<ItemStack> avail = MaidAssemblyService.collectAvailableItems(maid);
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

    // ═══════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════

    /** 获取当前原料: 中间产物槽优先 → 原材料槽 */
    private ItemStack getCurrentInput(MaidAssemblyInventory inv) {
        ItemStack inter = inv.getStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT);
        if (!inter.isEmpty()) return inter;
        return inv.getStackInSlot(MaidAssemblyInventory.MATERIAL_SLOT);
    }

    /** 消耗原料: 从中间槽(优先)或材料槽扣除1个 */
    private void consumeInput(EntityMaid maid, MaidAssemblyInventory inv, ItemStack input) {
        ItemStack inter = inv.getStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT);
        if (!inter.isEmpty() && ItemStack.isSameItemSameTags(inter, input)) {
            inter.shrink(1);
            inv.setStackInSlot(MaidAssemblyInventory.INTERMEDIATE_SLOT, inter);
        } else {
            ItemStack mat = inv.getStackInSlot(MaidAssemblyInventory.MATERIAL_SLOT);
            if (!mat.isEmpty()) {
                mat.shrink(1);
                inv.setStackInSlot(MaidAssemblyInventory.MATERIAL_SLOT, mat);
            }
        }
    }

    private ItemStack insertOrStack(MaidAssemblyInventory inv, int slot, ItemStack stack) {
        ItemStack ex = inv.getStackInSlot(slot);
        if (ex.isEmpty()) { inv.setStackInSlot(slot, stack.copy()); return ItemStack.EMPTY; }
        if (ItemStack.isSameItemSameTags(ex, stack) && ex.getCount() + stack.getCount() <= ex.getMaxStackSize()) {
            ex.grow(stack.getCount()); inv.setStackInSlot(slot, ex); return ItemStack.EMPTY;
        }
        return stack;
    }

    private void consumeFromBackpack(EntityMaid maid, ItemStack target) {
        // 1. 背包
        var bp = maid.getAvailableBackpackInv();
        for (int s = 0; s < bp.getSlots(); s++) {
            ItemStack stack = bp.getStackInSlot(s);
            if (ItemStack.isSameItemSameTags(stack, target)) {
                bp.extractItem(s, 1, false);
                return;
            }
        }
        // 2. 附近容器 — 只消耗1个
        if (maid.level() != null) {
            consumeFromNearby(maid, target);
        }
    }

    /** 从附近容器消耗1个指定物品 */
    private void consumeFromNearby(EntityMaid maid, ItemStack target) {
        var level = maid.level();
        int r = MaidAssemblyService.SEARCH_RADIUS;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    var be = level.getBlockEntity(maid.blockPosition().offset(dx, dy, dz));
                    if (be instanceof net.minecraft.world.Container container) {
                        for (int s = 0; s < container.getContainerSize(); s++) {
                            ItemStack st = container.getItem(s);
                            if (ItemStack.isSameItemSameTags(st, target)) {
                                container.removeItem(s, 1);
                                be.setChanged();
                                return;
                            }
                        }
                    }
                }
    }

    /** 产物分发: 背包 → 隙间 → 扔地上 */
    private void deposit(EntityMaid maid, ItemStack stack) {
        if (stack.isEmpty()) return;
        stack = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), stack, false);
        if (!stack.isEmpty()) {
            var wireless = littlemaidmoreaction.littlemaidmoreaction.vanilla.input.container.WirelessChestSpace.getWirelessHandler(maid);
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
