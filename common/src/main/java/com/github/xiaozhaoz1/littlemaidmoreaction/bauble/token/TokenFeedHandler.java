package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@net.neoforged.fml.common.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class TokenFeedHandler {

    private TokenFeedHandler() {}

    /**
     * 玩家**右键喂女仆** Token (用户需求): +2 饱食度 · 回 5 血 · 30s 缓慢恢复 I ✓
     *
     * <p>接线方式与 {@code event/FeedMaidWatermelonHandler} 完全同款 (TLM {@link InteractMaidEvent}
     * 只在"已驯服 + 主人右键女仆"时 fire ✓)。
     * <p>⚠ 效果数值**不在这里重复实现** ⇒ 直接调 {@link TokenItem#applyEffects} (与"吃"共用一套 ✓,
     * 免得两处漂移 ✗ — 见 bauble/README 陷阱 D "效果要可重放安全" ✓)。
     */
    // ⚠ SubscribeEvent 的包名**也分平台** ✗ (forge: net.minecraftforge.eventbus.api / neo: net.neoforged.bus.api)
//? if 1.20.1 {
    @net.minecraftforge.eventbus.api.SubscribeEvent
//?} else {
    @net.neoforged.bus.api.SubscribeEvent
//?}
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        // ★★ v79.74 关键修正 (用户点破 ✓): **游戏有两端, 拦截必须两端都做** ✓
        //   · TLM 的 `SwitchSittingEvent` 是 **common 类** ⇒ **客户端也会执行** ✓
        //   · 我原来第一行就 `if (player.level().isClientSide()) return;` ✗
        //     ⇒ **客户端侧从未被 cancel** ✗ ⇒ 客户端把自己坐下了 (玩家**看到**坐下 ✓)
        //     ⇒ 而服务端 `isMaidInSittingPose()` 仍是 false ✓ ⇒ 与日志实测完全吻合 ✓✓
        //   ⇒ 现在: **两端都 `setCanceled(true)` 抢占** ✓; 只有服务端做结算/日志/气泡 ✓
        if (!player.isDiscrete()) return;          // 蹲下 + 右键 才管 ✓ (两端判定一致 ✓)
        ItemStack stack = event.getStack();
        if (!isToken(stack)) {
            if (!player.level().isClientSide()) {
                LittleMaidMoreAction.LOGGER.debug("[Token] 蹲下右键女仆但不是 Token: {}", stack.getItem());
            }
            return;
        }

        // ★ 抢占: **两端都做** ✓ ⇒ TLM 的坐下 (LOWEST) 与 openMaidGui 都被跳过 ✓
        event.setCanceled(true);

        // 客户端: 抢占即结束 ✓ (不做结算/日志 ⇒ 避免双份日志与双扣 ✓)
        if (player.level().isClientSide()) return;

        EntityMaid maid = event.getMaid();
        if (maid == null) return;

        // 结算走 feedOnce ✓ (同 tick 只喂一次 ✓ —— 上面已抢占, 这里护栏只负责"不双喂" ✓)
        if (!TokenItem.feedOnce(maid, player, stack)) return;
        // ★ v79.74 (用户实测"喂了没提示"): **入口/结果必留 INFO 痕迹** — 外部交互路径禁止全静默 ✓
        //   (#358 教训: 我原版只留了气泡 ⇒ 气泡被 showInfo 的"同文本 5s→10s→30s + 全局 3s CD"静默压掉 ✗
        //    就完全查不到出了什么事 ✓)
        LittleMaidMoreAction.LOGGER.info("[Token] 已喂女仆: 饱食={} 血={} 位置={}",
                maid.getHunger(), maid.getHealth(), maid.blockPosition().toShortString());
        // 提示: **女仆气泡** (本项目喂食既有风格 — 与西瓜喂食同款 ✓)。
        // ⚠ **不加聊天框消息** ✗ (用户裁定: "我喂个 token 为什么还要聊天框提示" ✓);
        //   而"**手持对准女仆**"的 HUD 提示走 TLM 的 `ILittleMaid#addMaidTips` ✓
        //   (见 api/LittleMaidMoreActionExtension ✓ — 那才是金苹果提示的真实来源 ✓)
        // ⚠ **抢占必须最先做** (全部既有 handler 都这么做 ✓): 取消事件 ⇒ TLM 的 `post(event).isCanceled()`
        //   短路 ⇒ ① 不落进 `openMaidGui(...)` (不同时弹界面 ✓) ② **后面 LOWEST 优先级的 "坐下"
        //   (`SwitchSittingEvent`) 被跳过** ✓✓ —— 这正是用户观察到"金苹果能拦截坐下"的机制 ✓
        //   (放在气泡之后有风险: 气泡若抛异常 ⇒ 坐下的拦截就丢了 ✗)
        event.setCanceled(true);
        MaidChatBubbleApi.showInfo(maid, Component.translatable("bubble.littlemaidmoreaction.token_feed"));
    }

    /** 是否 Token (物品比对 ✓ — 与 LmaItems.TOKEN 一致) */
    private static boolean isToken(ItemStack stack) {
//? if 1.20.1 {
        return stack.is(LmaItems.TOKEN.get());
//?} else {
        return stack.is(LmaItems.TOKEN.get());
//?}
    }

    /**
     * **诊断专用** (v79.74, 用户实测"还是坐下" ✓): 与 TLM 的坐下 (`SwitchSittingEvent`) **同优先级 LOWEST** ✓。
     *
     * <p>目的: 一次测试就能判定 **cancel 是否真的阻止了 LOWEST 监听器收到事件** ✓ ——
     * · 若本方法**收不到** (日志无此行) ⇒ cancel 生效 ✓ ⇒ 坐下另有来源 ✗ (继续查)
     * · 若收到且 `canceled=true` ⇒ **NeoForge 把已取消事件也投递给了 LOWEST** ✗ ⇒ 那就必须换手段
     *   (改为"喂完在 tick 末撤销坐姿"为主 ✓ —— 该兜底已在 `TokenItem.feedOnce` 里 ✓)
     * · 若收到且 `canceled=false` ⇒ 我的 cancel 没生效/被重置 ✗
     */
    //? if 1.20.1 {
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
//?} else {
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
//?}
    public static void onInteractMaidLowest(InteractMaidEvent event) {
        if (event.getPlayer().level().isClientSide()) return;
        if (!isToken(event.getStack())) return;
        LittleMaidMoreAction.LOGGER.info("[Token][诊断] LOWEST 收到事件: canceled={} sneaking={} item={}",
                event.isCanceled(), event.getPlayer().isShiftKeyDown(), event.getStack().getItem());
    }
}
