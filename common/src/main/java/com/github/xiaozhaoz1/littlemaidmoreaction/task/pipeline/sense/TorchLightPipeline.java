package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.PassiveSignalSkeleton;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
//? if 1.20.1 {
import net.minecraftforge.items.ItemHandlerHelper;
//?} else {
import net.neoforged.neoforge.items.ItemHandlerHelper;
//?}
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * v79.47: 黑暗自动点亮被动任务 — DARKNESS 信号 → 背包火把/提灯换副手。
 *
 * <p>点亮后 tick 自检 (不依赖 CLEAR 信号): 亮度恢复 (>= darkness_threshold) 且
 * 周围 5 格无怪 → 火把换回背包 (防黑暗守夜)。副手被占用 (非火把/提灯) 时不覆盖。
 *
 * <p>2026-08-11c (#9 用户裁定): 恢复时周围有怪 → 火把拿回后从背包补盾 (原拿回后
 * 副手空 — 有怪却无盾; 有盾女仆 onSignal 本就不被换, 无盾女仆被换火把后恢复裸奔)。
 */
public final class TorchLightPipeline implements PassiveSignalSkeleton {

    /** 副手占用的灯类物品 — 火把/灵魂火把/提灯/灵魂提灯 (点亮判定 + 换回判定) */
    private static boolean isLightItem(ItemStack s) {
        Item i = s.getItem();
        return i == net.minecraft.world.item.Items.TORCH
                || i == net.minecraft.world.item.Items.SOUL_TORCH
                || i == net.minecraft.world.item.Items.LANTERN
                || i == net.minecraft.world.item.Items.SOUL_LANTERN;
    }

    /** 副手是食物 (进食占用判定 — v79.58 用户裁定: 黑暗时食物等吃完不替换) */
//? if 1.20.1 {
    private static boolean isFood(ItemStack s) {
        return s.isEdible();
    }
//?} else {
    private static boolean isFood(ItemStack s) {
        return s.has(net.minecraft.core.component.DataComponents.FOOD);
    }
//?}

    /** 重新点火节流 (tick) — v79.58 用户裁定: 黑暗时每 100t 检测副手, 空才点火 (防火把↔食物来回抖动) */
    private static final int REPICK_INTERVAL = 100;

    /** 点火 — 背包找火把/提灯 → 副手 (v79.58 提取: onSignal 首次 + tick 被顶后自愈共用) */
    private static boolean lightUp(ServerLevel world, EntityMaid maid) {
        // 副手已灯 (残留/玩家手动装备) → 跳过 — 防 setItemInHand 覆盖销毁旧火把
        if (isLightItem(maid.getOffhandItem())) return true;
        // v79.58 (用户裁定修订): 副手非灯 (食物/其他) → 先腾空放回背包 (不吞) 再放灯
        ItemStack off = maid.getOffhandItem();
        if (!off.isEmpty()) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), off, false);
            if (!rest.isEmpty()) maid.spawnAtLocation(rest);
        }
        var inv = maid.getAvailableBackpackInv();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (isLightItem(s) && s.getCount() > 0) {
                ItemStack torch = inv.extractItem(i, 1, false);
                if (!torch.isEmpty()) {
                    maid.setItemInHand(InteractionHand.OFF_HAND, torch);
                }
                return true;
            }
        }
        // 背包无灯 → 不亮 (女仆没带火把)
        return false;
    }

    @Override public String taskType() { return "torch_light"; }
    /** 点亮后需每 tick 自检恢复条件 (亮度/怪) — 长运行 */
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return okSignals(Set.of(Signals.ENV_DARKNESS));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, String signal) {
        if (!Signals.ENV_DARKNESS.equals(signal)) return;
        if (!(maid.level() instanceof ServerLevel world)) return;
        ItemStack off = maid.getOffhandItem();
        // v79.58 (用户裁定修订): 副手其他 (盾) → 不覆盖; 食物直接顶 (lightUp 腾食物放灯)

        // v79.48 修复 #10.1②: submitPassive 才有 in_progress → GMPM tick 驱动恢复逻辑 (否则火把永久插副手)
        if (!off.isEmpty() && !isLightItem(off) && !isFood(off)) return;
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.submitPassive(maid, taskType());

        // 副手已是灯类 (上次触发残留/玩家手动装备) → 已点亮, 跳过换新 —
        // 防 setItemInHand 直接覆盖销毁旧火把 (每次重复 DARKNESS 边沿销毁 1 根, 堆叠 N 根损失 N-1)
        lightUp(world, maid);
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        // v79.58 (用户裁定修订): 整个检查 100t 节流 (非每 tick — 亮度/副手状态)
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                .shouldFire(maid, "torch_check", REPICK_INTERVAL)) {
            return;
        }
        ItemStack off = maid.getOffhandItem();
        // 黑暗持续判定 (对齐 DARKNESS 阈值; v79.58 用户裁定: 黑暗时管线自轮询维护 —
        // 被进食顶掉火把后自动重新点火, 不再依赖边沿重发)
        boolean dark = world.getMaxLocalRawBrightness(maid.blockPosition())
                < com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig.ENV_DARKNESS_THRESHOLD.get();
        boolean monsterNearby = !world.getEntitiesOfClass(Monster.class,
                AABB.ofSize(maid.position(), 10, 10, 10)).isEmpty();

        if (!dark && !monsterNearby) {
            // 恢复条件: 亮度恢复且 5 格内无怪 (代码 !dark && !monsterNearby 语义)
            if (!isLightItem(off)) {
                // 无灯也天亮 → 闭环终止 (跨 session 残留/火把被消耗)
                com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancelPassive(maid, taskType());
                return;
            }
            // 火把换回背包 — 循环各槽 (insertItem 同堆叠合并语义, 单槽 insert 只试该槽); 全满 → 继续拿 (保底不丢)
            var inv = maid.getAvailableBackpackInv();
            ItemStack leftover = off;
            for (int i = 0; i < inv.getSlots() && !leftover.isEmpty(); i++) {
                leftover = inv.insertItem(i, leftover, false);
            }
            if (leftover.isEmpty()) {
                maid.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                // 2026-08-11c (#9 用户裁定): 周围有怪 → 火把拿回后从背包补盾 —
                // v79.48 修复 #10.1②: 恢复完成 → cancelPassive 闭环 (终止任务, 下次 DARKNESS 再触发)
                com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.cancelPassive(maid, taskType());
            }
            return;
        }
        // ── 黑暗/有怪持续 (v79.58 用户裁定修订: 100t 节流内, 空/食物 → 直接顶 (lightUp 腾副手), 其他 → 不替换) ──
        if (isLightItem(off)) {
            return;  // 已点亮
        }
        if (off.isEmpty() || isFood(off)) {
            // 副手空或食物 → 顶 (食物放回背包, lightUp 内部腾) + 点火; 背包无灯 → 下轮 100t 再试
            lightUp(world, maid);
            return;
        }
        // 其他占用 (盾/工具等) → 不替换 (用户裁定保留)
    }

}
