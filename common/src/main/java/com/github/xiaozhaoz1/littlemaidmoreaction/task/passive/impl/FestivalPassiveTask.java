package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import net.minecraft.world.entity.LivingEntity;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveTask;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.DailyDedup;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.FestivalTable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.util.Set;

/**
 * 节日气泡被动 (v79.61x 脱管线迁移 — 原 FestivalPipeline, 零 tick 纯触发型) —
 * FESTIVAL_ENTER 信号 → 节日文案气泡 (showTrigger 100t 节流)。
 *
 * <p>per-maid 当天首收去重: PD 存上次触发日期 (EpochDay long — 完整日期含年份),
 * 同天静默, 跨天/跨年 (EpochDay 不同) 再触发; 每天覆盖天然闭环。
 * <b>坑 (用户裁定)</b>: 去重键禁存 month/day — 明年同日月日相同 → 误判同天 → 永久静默。
 * stateless 广播 (Broadcaster 每轮查表非空即发) 配合: 女仆错过广播后上线首收即触发。
 */
public final class FestivalPassiveTask implements PassiveTask {

    /** 去重键: 上次触发日期 (EpochDay long) — 每天覆盖, 无残留 (v79.55 收编 TaskKeys) */
    static final String LAST_ANNOUNCE_KEY = TaskKeys.FESTIVAL_DAY;

    /** 去重判定纯函数 — 委托 {@link DailyDedup} (public: 测试跨包引用, v79.61x 脱管线) */
    public static boolean shouldAnnounce(long lastEpochDay, LocalDate today) {
        return DailyDedup.shouldAnnounce(lastEpochDay, today);
    }

    /** v79.62 调试命令直触发: 绕过日期/全局限频/per-maid去重, 直接给礼物+气泡 */
    public static void debugTrigger(ServerLevel level, EntityMaid maid, FestivalTable.Festival f) {
        java.util.List<String> foods = f.foods();
        if (!foods.isEmpty()) {
            String foodId = foods.get(new java.util.Random().nextInt(foods.size()));
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(foodId);
            if (rl != null) {
                net.minecraft.world.item.Item item = null;
//? if 1.20.1 {
                item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
//?} else {
                item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
//?}
                if (item != null) {
                    LivingEntity owner = maid.getOwner();
                    if (owner != null) {
                        owner.spawnAtLocation(new net.minecraft.world.item.ItemStack(item));
                    }
                }
            }
        }
        String msg = f.text() == null || f.text().isEmpty() ? f.name() : f.text();
        MaidChatBubbleApi.showTrigger(maid, msg);
    }

    @Override public String taskType() { return "festival"; }

    @Override public int cooldown() { return 0; }

    @Override
    public Set<String> needsSignals() {
        return Set.of(Signals.ENV_FESTIVAL_ENTER);
    }

    @Override
    public void trigger(ServerLevel level, EntityMaid maid, String signalId) {
        if (!Signals.ENV_FESTIVAL_ENTER.equals(signalId)) return;
        LocalDate today = LocalDate.now();
        FestivalTable.Festival f = FestivalTable.lookup(today);
        if (f == null) return;  // 表空/被清 — 无节日可报

        // 当天首收去重 (EpochDay 完整日期; 同天静默, 跨天/跨年再触发)
        long stored = maid.getPersistentData().getLong(LAST_ANNOUNCE_KEY);
        if (!shouldAnnounce(stored, today)) return;

        // v79.62.1 用户裁定: 每只女仆都送礼物, 但只给自己主人 (去全局限频 — 原 N 只只报 1 只;
        // per-maid 去重保留: 当天每只女仆只送一次)
        long epoch = today.toEpochDay();
        String msg = f.text() == null || f.text().isEmpty() ? f.name() : f.text();
        // 节日礼物: 从食物表随机 spawn 一份给自己主人 (仅当有主人)
        LivingEntity owner = maid.getOwner();
        if (owner != null) {
            java.util.List<String> foods = f.foods();
            if (!foods.isEmpty()) {
                String foodId = foods.get(new java.util.Random().nextInt(foods.size()));
                net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(foodId);
                if (rl != null) {
                    net.minecraft.world.item.Item item = null;
//? if 1.20.1 {
                    item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
//?} else {
                    item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
//?}
                    if (item != null) {
                        owner.spawnAtLocation(new net.minecraft.world.item.ItemStack(item));
                    }
                }
            }
        }
        MaidChatBubbleApi.showTrigger(maid, msg);
        maid.getPersistentData().putLong(LAST_ANNOUNCE_KEY, epoch);
    }
}
