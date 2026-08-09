package com.github.xiaozhaoz1.littlemaidmoreaction.ai.context;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;
//? if !1.20.1 {
import net.minecraft.core.component.DataComponents;
//?}

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
//?} else {
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * 追踪主人最近吃过的食物 (v11)。
 *
 * <p>订阅 {@link LivingEntityUseItemEvent.Finish}，当玩家吃完食物时
 * 将食物名写入玩家的 PersistentData，供 AI 上下文读取。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = "littlemaidmoreaction")
//?} else {
@EventBusSubscriber(modid = "littlemaidmoreaction")
//?}
public final class OwnerFoodTracker {

    private OwnerFoodTracker() {}

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack stack = event.getItem();
//? if 1.20.1 {
        if (!stack.isEdible()) return;
//?} else {
        if (!stack.has(DataComponents.FOOD)) return;
//?}

        // 存储到玩家 PersistentData
        String foodName = stack.getDisplayName().getString();
        player.getPersistentData().putString(TaskKeys.LAST_FOOD, foodName);
    }
}
