package littlemaidmoreaction.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.runtime.TaskDispatcher;
import littlemaidmoreaction.littlemaidmoreaction.task.pipeline.ArmTransferPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 木棍标记容器 + 右键女仆启动 arm_transfer (v53: 移出 compat/create)。
 * 木棍获取/容器判断/任务类型门控统一见 {@link StickBindUtil} (v67.1)。
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class ArmTransferSetupHandler {

    private ArmTransferSetupHandler() {}

    // ── ① 木棍右键容器: 标记取出点/放入点 ──

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!StickBindUtil.isMarkItem(held)) return;
        if (event.getLevel().isClientSide()) return;

        BlockPos pos = event.getPos();
        if (!StickBindUtil.isContainer(event.getLevel(), pos)) return;

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
        ItemStack held = StickBindUtil.getStickStack(player);
        if (held == null) return;
        if (maid.level().isClientSide) return;

        if (!StickBindUtil.checkTaskType(maid, "arm_transfer", player)) return;

        CompoundTag tag = held.getOrCreateTag();
        BlockPos takePos = readPos(tag, "take");
        BlockPos depositPos = readPos(tag, "deposit");

        if (takePos == null) { player.sendSystemMessage(comp("§c请先用木棍右键容器标记取出点")); return; }
        if (depositPos == null) { player.sendSystemMessage(comp("§c请再用木棍右键另一个容器标记放入点")); return; }
        if (maid.getAvailableInv(false).getSlots() <= 0) { player.sendSystemMessage(comp("§c女仆没有背包")); return; }

        var data = maid.getPersistentData();
        data.put(ArmTransferPipeline.KEY_TAKE, NbtUtils.writeBlockPos(takePos));
        data.put(ArmTransferPipeline.KEY_DEPOSIT, NbtUtils.writeBlockPos(depositPos));

        TaskDispatcher.submit(maid, "arm_transfer", null, 0);

        tag.remove("take");
        tag.remove("deposit");

        event.setCanceled(true);
        player.sendSystemMessage(comp("§a女仆开始搬运: " + takePos.toShortString() + " → " + depositPos.toShortString()));
    }

    // ── 工具 ──

    private static BlockPos readPos(CompoundTag tag, String key) {
        return tag.contains(key) ? NbtUtils.readBlockPos(tag.getCompound(key)) : null;
    }

    private static net.minecraft.network.chat.Component comp(String s) {
        return net.minecraft.network.chat.Component.literal(s);
    }
}
