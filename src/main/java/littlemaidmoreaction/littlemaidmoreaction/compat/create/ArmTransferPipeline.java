package littlemaidmoreaction.littlemaidmoreaction.compat.create;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskMemory;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public class ArmTransferPipeline implements TaskPipeline {
    static final String KEY_PHASE = "lma_arm_phase";
    static final String KEY_SOURCE = "lma_arm_source";
    static final String KEY_TARGET = "lma_arm_target";
    private static final String KEY_ITEM = "lma_arm_item";
    private static final List<TaskStep> STEPS = List.of(
        new TaskStep("take", "提取物品", StepType.COLLECT, List.of()),
        new TaskStep("carry", "搬运中", StepType.WAIT, List.of("take")),
        new TaskStep("deposit", "放入目标", StepType.INTERACT, List.of("carry"))
    );

    enum Phase { NAV_SOURCE, EXTRACT, NAV_TARGET, DEPOSIT }

    @Override public String taskType() { return "arm_transfer"; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        var d = maid.getPersistentData();
        if (!d.contains(KEY_SOURCE) || !d.contains(KEY_TARGET)) return PipelineResult.failed("坐标未设置");
        BlockPos src = NbtUtils.readBlockPos(d.getCompound(KEY_SOURCE));
        BlockPos tgt = NbtUtils.readBlockPos(d.getCompound(KEY_TARGET));
        if (!ArmTransferService.isValidContainer(maid, src)) return PipelineResult.failed("源容器无效");
        if (!ArmTransferService.isValidContainer(maid, tgt)) return PipelineResult.failed("目标容器无效");
        if (!ArmTransferService.hasInventorySpace(maid)) return PipelineResult.failed("背包已满");
        setPhase(maid, Phase.NAV_SOURCE);
        return PipelineResult.ok("就绪");
    }

    @Override public PipelineResult execute(ServerLevel l, EntityMaid m, PipelineContext c) { return PipelineResult.ok(""); }
    @Override public List<TaskStep> steps() { return STEPS; }

    public static TaskResult tick(ServerLevel world, EntityMaid maid, BlockPos pos, CompoundTag data) {
        var d = maid.getPersistentData();
        if (!d.contains(KEY_SOURCE) || !d.contains(KEY_TARGET)) { cleanup(maid); return TaskResult.FAILED; }
        BlockPos src = NbtUtils.readBlockPos(d.getCompound(KEY_SOURCE));
        BlockPos tgt = NbtUtils.readBlockPos(d.getCompound(KEY_TARGET));
        return switch (readPhase(d)) {
            case NAV_SOURCE -> navTo(world, maid, src, Phase.EXTRACT);
            case EXTRACT    -> doExtract(maid, src);
            case NAV_TARGET -> navTo(world, maid, tgt, Phase.DEPOSIT);
            case DEPOSIT    -> doDeposit(maid, src, tgt);
        };
    }

    private static TaskResult navTo(ServerLevel world, EntityMaid maid, BlockPos target, Phase next) {
        if (arrived(maid, target)) { setPhase(maid, next); return TaskResult.CONTINUE; }
        LmaTaskMemory.setNavTarget(maid, target);
        LmaTaskMemory.setNavStartTick(maid, world.getGameTime());
        // 每 tick 直接设 WALK_TARGET, 不等行为系统的 100tick 检查周期
        net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
        return TaskResult.CONTINUE;
    }

    private static TaskResult doExtract(EntityMaid maid, BlockPos src) {
        var item = ArmTransferService.readSourceItem(maid, src);
        if (item.isEmpty()) { cleanup(maid); return TaskResult.SUCCESS; }
        int count = ArmTransferService.computeExtractCount(maid, item);
        if (count <= 0) { cleanup(maid); return TaskResult.SUCCESS; }
        ArmTransferService.executeExtract(maid, src, item, count);
        maid.swing(InteractionHand.MAIN_HAND);
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
        maid.getPersistentData().putString(KEY_ITEM, id != null ? id.toString() : item.getDescriptionId());
        setPhase(maid, Phase.NAV_TARGET);
        return TaskResult.CONTINUE;
    }

    private static TaskResult doDeposit(EntityMaid maid, BlockPos src, BlockPos tgt) {
        String itemId = maid.getPersistentData().getString(KEY_ITEM);
        var mItem = itemId.isEmpty() ? ArmTransferService.readMaidItem(maid) : findMaidItem(maid, itemId);
        if (mItem.isEmpty()) {
            if (ArmTransferService.isSourceEmpty(maid, src)) { cleanup(maid); return TaskResult.SUCCESS; }
            setPhase(maid, Phase.NAV_SOURCE); return TaskResult.CONTINUE;
        }
        int count = ArmTransferService.computeDepositCount(maid, tgt, mItem);
        if (count <= 0) { cleanup(maid); return TaskResult.SUCCESS; }
        ArmTransferService.executeDeposit(maid, tgt, mItem, count);
        maid.swing(InteractionHand.MAIN_HAND);
        if (ArmTransferService.isSourceEmpty(maid, src)) { cleanup(maid); return TaskResult.SUCCESS; }
        setPhase(maid, Phase.NAV_SOURCE); return TaskResult.CONTINUE;
    }

    private static boolean arrived(EntityMaid m, BlockPos p) { return p.distToCenterSqr(m.position()) < 9.0; }
    private static Phase readPhase(CompoundTag d) { try { return Phase.valueOf(d.getString(KEY_PHASE)); } catch (Exception e) { return Phase.NAV_SOURCE; } }
    private static void setPhase(EntityMaid m, Phase p) { m.getPersistentData().putString(KEY_PHASE, p.name()); }

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

    public static void cleanup(EntityMaid maid) {
        var d = maid.getPersistentData();
        d.remove(KEY_PHASE); d.remove(KEY_SOURCE); d.remove(KEY_TARGET);
        d.remove(KEY_ITEM); d.remove("lma_flow_task"); d.remove("lma_flow_state");
        LmaTaskMemory.clearAllNav(maid);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel sl : event.getServer().getAllLevels()) {
            for (var e : sl.getAllEntities()) {
                if (!(e instanceof EntityMaid maid)) continue;
                var d = maid.getPersistentData();
                if (!"arm_transfer".equals(d.getString("lma_flow_task"))) continue;
                if (!"in_progress".equals(d.getString("lma_flow_state"))) continue;
                tick(sl, maid, maid.blockPosition(), d);
            }
        }
    }
}
