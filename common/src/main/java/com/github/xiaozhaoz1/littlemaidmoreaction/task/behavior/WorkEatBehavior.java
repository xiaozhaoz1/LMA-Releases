package com.github.xiaozhaoz1.littlemaidmoreaction.task.behavior;
//? if !1.20.1 {
import net.minecraft.core.component.DataComponents;
//?}

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 工作吃食物Behavior — 通用, 任何长时间任务可引用.
 *
 * <p>好感越高吃得越少 (效率越高):
 * <pre>
 *   Lv.0 →  100 tick (5s)
 *   Lv.1 →  200 tick (10s)
 *   Lv.2 →  300 tick (15s)
 *   Lv.3 →  600 tick (30s)
 * </pre>
 *
 * <p>用法: 在 IMaidTask.createBrainTasks() 中添加:
 * <pre>{@code
 *   list.add(Pair.of(3, new WorkEatBehavior()));
 * }</pre>
 */
public class WorkEatBehavior extends MaidCheckRateTask {

    public WorkEatBehavior() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid)) return false;
        setMaxCheckRate(switch (maid.getFavorabilityManager().getLevel()) {
            case 3 -> 600; case 2 -> 300; case 1 -> 200; default -> 100;
        });
//? if 1.20.1 {
        if (maid.getMainHandItem().isEdible() || maid.getOffhandItem().isEdible()) return true;
//?} else {
        if (maid.getMainHandItem().has(DataComponents.FOOD) || maid.getOffhandItem().has(DataComponents.FOOD)) return true;
//?}
        var bp = maid.getAvailableBackpackInv();
        for (int i = 0; i < bp.getSlots(); i++) {
            ItemStack st = bp.getStackInSlot(i);
//? if 1.20.1 {
            if (st.isEdible()) {
//?} else {
            if (st.has(DataComponents.FOOD)) {
//?}
                InteractionHand hand = maid.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack old = maid.getItemInHand(hand);
                if (!old.isEmpty()) {
                    for (int j = 0; j < bp.getSlots(); j++)
                        if (bp.getStackInSlot(j).isEmpty()) { bp.setStackInSlot(j, old.copy()); break; }
                }
                maid.setItemInHand(hand, bp.extractItem(i, 1, false));
                return true;
            }
        }
        return false;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        ItemStack food = maid.getMainHandItem();
//? if 1.20.1 {
        if (!food.isEdible()) food = maid.getOffhandItem();
//?} else {
        if (!food.has(DataComponents.FOOD)) food = maid.getOffhandItem();
//?}
//? if 1.20.1 {
        if (food.isEdible()) maid.eat(level, food);
//?} else {
        if (food.has(DataComponents.FOOD)) maid.eat(level, food);
//?}
    }
}
