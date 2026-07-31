package littlemaidmoreaction.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.pipeline.BlockInteractPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.runtime.TaskDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 木棍标记交互方块 + 右键女仆绑定 block_interact 任务。
 *
 * <p><b>与 ArmTransferSetupHandler 分离</b>:
 * <ul>
 *   <li>ArmTransfer 处理 isContainer()==true 的方块</li>
 *   <li>BlockInteract 处理 isContainer()==false 的方块</li>
 *   <li>各自检查 taskType, 互不干扰</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class BlockInteractSetupHandler {

    /** 木棍 NBT key — 存储绑定的方块坐标 */
    static final String STICK_KEY = "lma_bind_pos";

    private BlockInteractSetupHandler() {}

    // ── ① 木棍右键非容器方块: 标记交互目标 ──

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!held.is(Items.STICK)) return;
        if (event.getLevel().isClientSide()) return;

        BlockPos pos = event.getPos();
        // 跳过容器 — ArmTransferSetupHandler 处理
        if (isContainer(event.getLevel(), pos)) return;

        CompoundTag tag = held.getOrCreateTag();
        tag.put(STICK_KEY, NbtUtils.writeBlockPos(pos));
        event.getEntity().sendSystemMessage(
            Component.literal("§a已标记交互方块: " + pos.toShortString()
                + " §7(右键有 block_interact 任务的女仆绑定)"));
    }

    // ── ② 木棍右键女仆: pos转存到maid → submit ──

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.STICK)) {
            held = player.getOffhandItem();
            if (!held.is(Items.STICK)) return;
        }
        if (maid.level().isClientSide) return;

        String taskType = LmaTaskTypeRegistry.extractTaskType(maid.getTask().getUid().getPath());
        if (!"block_interact".equals(taskType)) {
            String name = taskType != null ? taskType : "idle";
            player.sendSystemMessage(
                Component.literal("§c物品(木棍)不支持设置该任务(" + name + ")"));
            return;
        }

        CompoundTag tag = held.getOrCreateTag();
        if (!tag.contains(STICK_KEY)) {
            player.sendSystemMessage(
                Component.literal("§c请先用木棍右键一个交互方块(按钮/拉杆/门等)"));
            return;
        }
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound(STICK_KEY));

        // 写入 pipelineConfig (跨任务持久)
        CompoundTag cfg = BlockInteractPipeline.config(maid);
        cfg.put(BlockInteractPipeline.KEY_POS, NbtUtils.writeBlockPos(pos));

        TaskDispatcher.submit(maid, "block_interact", null, 0);

        tag.remove(STICK_KEY); // 清理木棍
        event.setCanceled(true);
        player.sendSystemMessage(
            Component.literal("§a女仆已绑定交互方块: " + pos.toShortString()
                + " §7(按键手动触发)"));
    }

    // ── 工具方法 ──

    /** 检查方块是否容器 (与 ArmTransferSetupHandler 逻辑一致) */
    private static boolean isContainer(LevelAccessor level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null) return false;
        for (var d : net.minecraft.core.Direction.values()) {
            if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).isPresent()) return true;
        }
        return false;
    }
}
