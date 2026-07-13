package littlemaidmoreaction.littlemaidmoreaction.compat.ai.context;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 追踪主人最近吃过的食物 (v11)。
 *
 * <p>订阅 {@link LivingEntityUseItemEvent.Finish}，当玩家吃完食物时
 * 将食物名写入玩家的 PersistentData，供 AI 上下文读取。
 */
@Mod.EventBusSubscriber(modid = "littlemaidmoreaction")
public final class OwnerFoodTracker {

    private OwnerFoodTracker() {}

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack stack = event.getItem();
        if (!stack.isEdible()) return;

        // 存储到玩家 PersistentData
        String foodName = stack.getDisplayName().getString();
        player.getPersistentData().putString("lma_last_food", foodName);
    }
}
