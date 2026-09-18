package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import net.minecraft.world.entity.LivingEntity;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveTask;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.DailyDedup;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.FestivalTable;
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
        // ★ v79.64 (用户要求 ✓): **命令路径也要留痕** — 原来只有气泡 ✗ ⇒ 送了礼物也查不到送了什么 ✓
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                "[LMA/Festival] 命令直触发 → 节日={} 女仆={} 主人={} 礼物池={}",
                f.name(), maid.getName().getString(),
                maid.getOwner() == null ? "无" : maid.getOwner().getName().getString(), f.foods());
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
                        // ★ v79.64 (用户裁定 A ✓ 2026-09-16): **礼物直接进主人背包** ✓ (原 spawnAtLocation
                        //   只把物品实体丢在脚下 ⇒ 用户以为"没送" ✗ — 实测日志/代码双证 ✓)
                        //   背包满 ⇒ 才掉出来兜底 ✓ (不丢东西 ✓)
                        net.minecraft.world.item.ItemStack gift = new net.minecraft.world.item.ItemStack(item);
                        // ★ v79.64 修平台差异 (javap 实证: forge 1.20.1 变体里**整段没编进去** ✗ —
                        //   原写法 `ServerPlayer.addItem` ✗ 该 API 在 1.20.1 位于 Player ⇒ Stonecutter 条件把整段判掉 ✗)
                        //   改用**两平台通用**的 `Player.getInventory().add(ItemStack)` ✓ (1.20.1/1.21.1 都在 ✓)
                        //   ⇒ 背包满再掉落兜底 ✓ (不丢东西 ✓)
                        if (owner instanceof net.minecraft.world.entity.player.Player p) {
                            if (!p.getInventory().add(gift)) owner.spawnAtLocation(gift);
                        } else {
                            owner.spawnAtLocation(gift);
                        }
                    }
                }
            }
        }
        String msg = f.text() == null || f.text().isEmpty() ? f.name() : f.text();
        // ★ v79.64 (用户实测「节日祝福气泡是黄色的，看不清」✗): showTrigger 里写死了 **`§e⚠ `** 警告前缀 ✗
        //   ⇒ 节日祝福**不是警告** ✓ 改走 showInfo ⇒ **默认黑色** ✓ 可读 ✓ (两条路径 debugTrigger/trigger 统一 ✓)
        MaidChatBubbleApi.showInfo(maid, net.minecraft.network.chat.Component.literal(msg), 100);
        // ★ v79.64 (用户实测「礼物收到了，但**聊天框没看见祝福**」✗): 命令路径只发了气泡 ✗ —
        //   和"命令路径没日志"是同一个坑 ✓ ⇒ 这里补上**聊天框祝福** ✓ (与 trigger 路径一致 ✓)
        LivingEntity dbgOwner = maid.getOwner();
        if (dbgOwner != null) {
            dbgOwner.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
        }
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
        // ★ v79.64 (用户裁定 2026-09-16): **全程出声** — 原实现四条静默路径 (无主人/空表/id 解析失败/
        //   物品不在注册表) 全部 silent ✗ ⇒ 用户只能看到"没送" ✗ 无从判断 ✓。现: 气泡 + **聊天框** +
        //   日志 (含**礼物名** ✓) ⇒ "送了什么"当场可见 ✓ 也可事后查日志 ✓。
        LivingEntity owner = maid.getOwner();
        String giftName = null;
        String failReason = null;
        if (owner == null) {
            failReason = "这只女仆没有绑定主人，礼物送不出去";
        } else {
            java.util.List<String> foods = f.foods();
            if (foods.isEmpty()) {
                failReason = "节日「" + f.name() + "」的礼物池是空的 (检查 festival.json)";
            } else {
                String foodId = foods.get(new java.util.Random().nextInt(foods.size()));
                net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(foodId);
                if (rl == null) {
                    failReason = "礼物 id 解析失败: " + foodId;
                } else {
                    net.minecraft.world.item.Item item = null;
//? if 1.20.1 {
                    item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
//?} else {
                    item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
//?}
                    if (item == null) {
                        failReason = "礼物 id 不在注册表里: " + foodId;
                    } else {
                        // ★ v79.64 (用户裁定 A ✓ 2026-09-16): **礼物直接进主人背包** ✓ (原 spawnAtLocation
                        //   只把物品实体丢在脚下 ⇒ 用户以为"没送" ✗ — 实测日志/代码双证 ✓)
                        //   背包满 ⇒ 才掉出来兜底 ✓ (不丢东西 ✓)
                        net.minecraft.world.item.ItemStack gift = new net.minecraft.world.item.ItemStack(item);
                        // ★ v79.64 修平台差异 (javap 实证: forge 1.20.1 变体里**整段没编进去** ✗ —
                        //   原写法 `ServerPlayer.addItem` ✗ 该 API 在 1.20.1 位于 Player ⇒ Stonecutter 条件把整段判掉 ✗)
                        //   改用**两平台通用**的 `Player.getInventory().add(ItemStack)` ✓ (1.20.1/1.21.1 都在 ✓)
                        //   ⇒ 背包满再掉落兜底 ✓ (不丢东西 ✓)
                        if (owner instanceof net.minecraft.world.entity.player.Player p) {
                            if (!p.getInventory().add(gift)) owner.spawnAtLocation(gift);
                        } else {
                            owner.spawnAtLocation(gift);
                        }
                        giftName = item.getDescription().getString();
                    }
                }
            }
        }
        // ★ v79.64 **用户裁定 (2026-09-16)**: 两条通道**分开** ✓
        //   ① **节日祝福** ⇒ 气泡 + **聊天框** ✓ (祝福文本 msg)
        //   ② **礼物** ⇒ **只写日志** ✓ (不塞聊天框 ✗) — 送成功/失败原因都进 log ✓ 便于事后追溯 ✓
        // ★ v79.64 (用户实测「节日祝福气泡是黄色的，看不清」✗): showTrigger 里写死了 **`§e⚠ `** 警告前缀 ✗
        //   ⇒ 节日祝福**不是警告** ✓ 改走 showInfo ⇒ **默认黑色** ✓ 可读 ✓ (两条路径 debugTrigger/trigger 统一 ✓)
        MaidChatBubbleApi.showInfo(maid, net.minecraft.network.chat.Component.literal(msg), 100);
        if (owner != null) {
            owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                "[LMA/Festival] 送礼 → 节日={} 主人={} 礼物={} 结果={}",
                f.name(), owner == null ? "无" : owner.getName().getString(),
                giftName == null ? "-" : giftName,
                failReason == null ? "已送出 ✓" : failReason);
        maid.getPersistentData().putLong(LAST_ANNOUNCE_KEY, epoch);
    }
}
