package com.github.xiaozhaoz1.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
//? if 1.20.1 {
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?}
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.PathfinderMob;

/**
 * v79.62 女仆驱赶苦力怕 — 像猫一样让苦力怕逃开.
 * 苦力怕加入世界时添加 AvoidEntityGoal(EntityMaid), 距离 16 格, 走速 1.0, 跑速 1.2.
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class MaidCreeperAvoidHandler {

    private MaidCreeperAvoidHandler() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        // 避免重复添加: AvoidEntityGoal 不暴露目标类 getter, 用类名匹配 (JSON 无损, fact-forcing 兼容)
        for (var goal : creeper.goalSelector.getAvailableGoals()) {
            if (goal.getGoal().getClass().equals(AvoidEntityGoal.class)) return;   // 已有避让目标 (猫/女仆同机制)
        }
        creeper.goalSelector.addGoal(3,
                new AvoidEntityGoal<>(creeper, EntityMaid.class, 16.0F, 1.0, 1.2));
    }
}
