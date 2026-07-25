package littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 装配事件: 木棍右键打开GUI + 魂符收放持久化.
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class MaidAssemblyEventHandler {

    private MaidAssemblyEventHandler() {}

    /** 木棍右键女仆 → 打开便携装配GUI (仅当女仆任务 == MaidAssemblyTask) */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractMaid(InteractMaidEvent event) {
        var player = event.getPlayer();
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.STICK)) {
            held = player.getOffhandItem();
            if (!held.is(Items.STICK)) return;
        }

        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide) return;

        if (!(maid.getTask() instanceof MaidAssemblyTask)) return;

        if (player instanceof ServerPlayer sp) {
            MaidAssemblyNetwork.openGui(sp, maid);
            event.setCanceled(true);
        }
    }

    /** 魂符收起女仆 → 强制写入装配库存到物品NBT */
    @SubscribeEvent
    public static void onMaidToItem(MaidAndItemTransformEvent.ToItem event) {
        EntityMaid maid = event.getMaid();
        var inv = MaidAssemblyInventory.of(maid);
        inv.saveToNBT(); // 确保最新状态写入 PersistentData
        CompoundTag data = event.getData();
        CompoundTag tag = maid.getPersistentData().getCompound("maid_assembly");
        if (!tag.isEmpty()) {
            data.put("lma_assembly", tag);
        }
    }

    /** 魂符放出女仆 → 从物品NBT恢复装配库存 */
    @SubscribeEvent
    public static void onMaidFromItem(MaidAndItemTransformEvent.ToMaid event) {
        CompoundTag data = event.getData();
        if (!data.contains("lma_assembly")) return;
        // 写入 ForgeData, 让 maid.load() 的 readAdditionalSaveData 自动恢复
        CompoundTag forge = data.getCompound("ForgeData");
        forge.put("maid_assembly", data.getCompound("lma_assembly"));
        data.put("ForgeData", forge);
    }
}
