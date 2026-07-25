package littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 木棍右键女仆 → 打开便携装配GUI (仅当女仆任务 == MaidAssemblyTask)。
 *
 * <p>优先级 HIGH: 在 ArmTransferSetupHandler (默认优先级) 之前拦截。
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class MaidAssemblyEventHandler {

    private MaidAssemblyEventHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractMaid(InteractMaidEvent event) {
        // 仅木棍触发
        var player = event.getPlayer();
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.STICK)) {
            held = player.getOffhandItem();
            if (!held.is(Items.STICK)) return;
        }

        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide) return;

        // 仅当女仆当前任务是 MaidAssemblyTask
        if (!(maid.getTask() instanceof MaidAssemblyTask)) return;

        // 打开GUI
        if (player instanceof ServerPlayer sp) {
            MaidAssemblyNetwork.openGui(sp, maid);
            event.setCanceled(true);
        }
    }
}
