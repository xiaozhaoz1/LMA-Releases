package com.github.xiaozhaoz1.littlemaidmoreaction.compat.patpat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.HaqiOwnerVoicePacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if 1.20.1 {
import net.minecraftforge.fml.ModList;
//?} else {
import net.neoforged.fml.ModList;
//?}

import java.util.concurrent.ThreadLocalRandom;

/**
 * PatPat (抚摸) 兼容模块 (v79.61x, 用户裁定) — 女仆被 PatPat 抚摸时的反应链。
 *
 * <p><b>检测链</b>: PatPat 的抚摸按键 (Shift+右键) 会取消 {@code KeyMapping.click} (PatPat
 * KeybindingMixin 实证) → 服务端 EntityInteractEvent 在抚摸期间根本不 fire → LMA 无法用
 * 交互事件感知抚摸。故反应链 = 客户端轮询 PatPat 状态表 ({@link PatPatReactionClient}) +
 * 自建 C2S 包 (PatPatReactionPacket) → 本类执行服务端反应。
 *
 * <p><b>反应 (用户裁定, 与挤奶好感同构)</b>: 好感 +1 + 爱心粒子 + <b>对主人哈气专用语音
 * (littlemaid_peco 包 idle 子集随机 — 同 HaqiOwnerVoicePacket 链路, 非 ha_1..5/laowu_1..5 原版清单)</b>
 * + 气泡 (随机 3 选 1, lang 可配置: tip.pat.1/2/3)。
 *
 * <p><b>门控</b>: 仅 PatPat 安装时激活 (CompatRegistry.doScan 经 checkModLoad 调用 init;
 * forge 1.20.1 无 PatPat 构建 [fabric-only] → 恒跳过, TLM 默认坐下行为不变)。
 *
 * <p><b>信任客户端模型</b> (与 PatPat 自身一致 — PatPat 服务端对 C2S 抚摸包零校验);
 * LMA 侧补轻校验: owner + 3 格距离 + per-maid 600t 节流 (服务端权威, 客户端节流仅为降载)。
 */
public final class PatPatCompat {

    private PatPatCompat() {}

    /** 反应 CD (tick, 30 秒) — 用户裁定同挤奶好感 CD 量级 */
    public static final int REACTION_CD_TICKS = 600;

    /** 反应 CD 键 (maid PersistentData, 存绝对截止 tick; 时钟回退/0 → 视为过期可发) */
    private static final String KEY_REACTION_CD = "lma_pat_last";

    /** 气泡文案键 (随机 3 选 1, lang 可配置) */
    private static final String[] TIP_KEYS = {"tip.pat.1", "tip.pat.2", "tip.pat.3"};

    /** 门控: PatPat 已安装 && 兼容开关未禁 (2026-08-16 用户裁定补 GUI 开关 — CompatConfigScreen
     * 模块表登记 patpat, CompatToggle disabled 可关; forge 1.20.1 无 PatPat 构建恒 false) */
    public static boolean isInstalled() {
        return com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("patpat")
                && ModList.get().isLoaded("patpat");
    }

    /**
     * 由 CompatRegistry.doScan 在 PatPat 已装时调用。
     * 目前服务端无注册项 (反应链由 C2S 包直接驱动, 无事件/无初始化副作用) — 保留入口,
     * 未来若加服务端逻辑 (如注册 TLM 扩展) 在此落点。
     */
    public static void init() {
        // 无操作 — 门控落点
    }

    /**
     * 服务端反应 (C2S 包 handle 调用) — 三道轻校验全过后执行。
     */
    public static void onReaction(ServerPlayer player, int maidId) {
        if (!isInstalled()) return;
        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(maidId);
        if (!(entity instanceof EntityMaid maid)) return;
        if (!maid.isOwnedBy(player)) return;
        if (maid.distanceToSqr(player) > 3.0 * 3.0) return;

        long now = level.getGameTime();
        long cdUntil = maid.getPersistentData().getLong(KEY_REACTION_CD);
        if (cdUntil > now) return;
        maid.getPersistentData().putLong(KEY_REACTION_CD, now + REACTION_CD_TICKS);

        // 好感 +1 (同挤奶)
        maid.getFavorabilityManager().add(1);
        // 爱心粒子 (同挤奶头顶 5 颗)
        level.sendParticles(ParticleTypes.HEART,
                maid.getX(), maid.getEyeY() + 0.4, maid.getZ(),
                5, 0.35, 0.2, 0.35, 0.05);
        // 音效 — 对主人哈气专用语音 (littlemaid_peco 包 idle 子集随机, 同 HaqiOwnerVoicePacket
        // 链路; 用户裁定: 不是 ha_1..5/laowu_1..5 原版清单, 是特别选的对主人语音), 音量走对主人配置
        HaqiOwnerVoicePacket.sendToTracking(maid, PassiveTaskConfig.HAQI_VOLUME_TO_OWNER.get().floatValue());
        // 气泡 (随机 3 选 1; showTrigger 自带 100t 节流, 实际由 600t 反应 CD 主导)
        MaidChatBubbleApi.showTrigger(maid, Component.translatable(
                TIP_KEYS[ThreadLocalRandom.current().nextInt(TIP_KEYS.length)]));
    }
}
