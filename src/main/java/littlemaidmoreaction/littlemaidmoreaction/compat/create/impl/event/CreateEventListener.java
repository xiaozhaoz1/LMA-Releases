package littlemaidmoreaction.littlemaidmoreaction.compat.create.impl.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.create.ArmTransferPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.service.TaskStateService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 女仆搬运事件 — 木棍标记容器 + 木棍右键女仆启动。
 *
 * <p>完全不拦截 Create 机械臂任何事件。
 * 仅在女仆当前任务是 arm_transfer 时，木棍右键才启动搬运。</p>
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class CreateEventListener {

    private CreateEventListener() {}

    // ── ① 木棍右键容器: 标记取出点/放入点 ──

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!held.is(Items.STICK)) return;
        if (event.getLevel().isClientSide()) return;

        BlockPos pos = event.getPos();
        if (!isContainer(event.getLevel(), pos)) return;

        CompoundTag tag = held.getOrCreateTag();
        BlockPos curTake = readPos(tag, "take");
        BlockPos curDep = readPos(tag, "deposit");

        if (pos.equals(curTake)) {
            tag.remove("take");
            tag.put("deposit", NbtUtils.writeBlockPos(pos));
            event.getEntity().sendSystemMessage(comp("§e放入点已标记: " + pos.toShortString()
                + " §7(右键另一个容器标记取出点 → 右键女仆开始)"));
        } else if (pos.equals(curDep)) {
            tag.remove("deposit");
            tag.put("take", NbtUtils.writeBlockPos(pos));
            event.getEntity().sendSystemMessage(comp("§b取出点已标记: " + pos.toShortString()
                + " §7(右键女仆开始搬运)"));
        } else {
            tag.remove("take");
            tag.put("take", NbtUtils.writeBlockPos(pos));
            event.getEntity().sendSystemMessage(comp("§b取出点已标记: " + pos.toShortString()
                + " §7(再右键同一容器→放入点)"));
        }
    }

    // ── ② 木棍右键女仆: 仅在 arm_transfer 任务时启动 ──

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.STICK)) { held = player.getOffhandItem(); if (!held.is(Items.STICK)) return; }
        if (maid.level().isClientSide) return;

        // ★ 必须女仆当前任务是 arm_transfer
        String taskType = LmaTaskTypeRegistry.extractTaskType(maid.getTask().getUid().getPath());
        if (!"arm_transfer".equals(taskType)) {
            player.sendSystemMessage(comp("§c请先将女仆任务切换为「搬运」"));
            return;
        }

        CompoundTag tag = held.getOrCreateTag();
        BlockPos takePos = readPos(tag, "take");
        BlockPos depositPos = readPos(tag, "deposit");

        if (takePos == null) { player.sendSystemMessage(comp("§c请先用木棍右键容器标记取出点")); return; }
        if (depositPos == null) { player.sendSystemMessage(comp("§c请再用木棍右键另一个容器标记放入点")); return; }
        if (maid.getAvailableInv(false).getSlots() <= 0) { player.sendSystemMessage(comp("§c女仆没有背包")); return; }

        var data = maid.getPersistentData();
        data.put("lma_arm_take", NbtUtils.writeBlockPos(takePos));
        data.put("lma_arm_deposit", NbtUtils.writeBlockPos(depositPos));
        data.putString("lma_arm_state", "TAKE");

        TaskStateService.init(maid, "arm_transfer", maid.level().getGameTime());

        tag.remove("take");
        tag.remove("deposit");

        event.setCanceled(true);
        player.sendSystemMessage(comp("§a女仆开始搬运: " + takePos.toShortString() + " → " + depositPos.toShortString()));
    }

    // ── ③ ServerTick: 驱动搬运 ──

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel sl : event.getServer().getAllLevels()) {
            for (var e : sl.getAllEntities()) {
                if (!(e instanceof EntityMaid maid)) continue;
                var d = maid.getPersistentData();
                if (!"arm_transfer".equals(d.getString("lma_flow_task"))) continue;
                if (!"in_progress".equals(d.getString("lma_flow_state"))) continue;
                TaskStateService.heartbeat(maid, sl.getGameTime());
                ArmTransferPipeline.tick(sl, maid);
            }
        }
    }

    // ── 工具 ──

    private static BlockPos readPos(CompoundTag tag, String key) {
        return tag.contains(key) ? NbtUtils.readBlockPos(tag.getCompound(key)) : null;
    }

    private static boolean isContainer(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null) return false;
        for (var d : net.minecraft.core.Direction.values()) {
            if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).isPresent()) return true;
        }
        return false;
    }

    private static net.minecraft.network.chat.Component comp(String s) {
        return net.minecraft.network.chat.Component.literal(s);
    }
}
