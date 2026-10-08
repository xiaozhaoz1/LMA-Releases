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
//? if 1.20.1 {
import net.minecraftforge.items.ItemHandlerHelper;
//?} else {
import net.neoforged.neoforge.items.ItemHandlerHelper;
//?}

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
        // 好感度消耗乘区 — 等级高吃得省 (检查间隔 = 100 / cost, 原手写 switch 收编)
        setMaxCheckRate((int) (100
                / com.github.xiaozhaoz1.littlemaidmoreaction.task.service.MaidFavorability.costMultiplier(maid)));
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
                // 先抽食物 (堆叠=1 时腾出槽 i), 旧物品优先入包 (含刚腾空的槽), 无空槽则掉落 — 不吞物品
                // (原实现: old.copy() 找空槽, 背包满时无处落 → setItemInHand 直接覆盖 → 永久丢失 H-1)
                ItemStack food = bp.extractItem(i, 1, false);
                if (!old.isEmpty()) {
                    boolean placed = false;
                    for (int j = 0; j < bp.getSlots(); j++)
                        if (bp.getStackInSlot(j).isEmpty()) { bp.setStackInSlot(j, old); placed = true; break; }
                    if (!placed) maid.spawnAtLocation(old);
                }
                maid.setItemInHand(hand, food);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
//? if 1.20.1 {
        InteractionHand eatHand = maid.getMainHandItem().isEdible()
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
//?} else {
        InteractionHand eatHand = maid.getMainHandItem().has(DataComponents.FOOD)
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
//?}
        ItemStack food = maid.getItemInHand(eatHand);
//? if 1.20.1 {
        if (!food.isEdible()) food = maid.getOffhandItem();
//?} else {
        if (!food.has(DataComponents.FOOD)) food = maid.getOffhandItem();
//?}
        // v79.62.3 修复 (用户实测: 加 LMA 后女仆把无限牛排吃没): 之前直接 maid.eat() →
        // LivingEntity.eat → ItemStack.consume 纯 shrink, 绕过 USE_REMAINDER (1.21.x 无限食物
        // 组件) → 牛排真消失. TLM 原生 MaidWorkMealTask 用 startUsingItem 走完整 use 流程
        // (completeUsingItem → finishUsingItem → USE_REMAINDER 返还) — 对齐之.
        // 食物留在手上让 vanilla 吃 (吃 32 tick 后自动 complete), 不清手槽不放回 (吃完自动处理).
//? if 1.20.1 {
        if (food.isEdible()) {
//?} else {
        if (food.has(DataComponents.FOOD)) {
//?}
            maid.startUsingItem(eatHand);
        }
    }

}
