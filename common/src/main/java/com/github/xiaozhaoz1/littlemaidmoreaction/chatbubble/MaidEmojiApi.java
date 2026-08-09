package com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidChatBubblePacket;
import net.minecraft.server.level.ServerLevel;

/**
 * 女仆表情气泡通用 API (v79.20) — 给任意管道/逻辑用的门面:
 * 写参数 (maid + 类型) → 经通用网络包 {@link MaidChatBubblePacket} 发到客户端 →
 * 客户端在 maid 实体头上加气泡 (TLM ChatBubbleRenderer 渲染)。
 *
 * <p>不绑 haqi — 任何管道想给女仆加表情气泡都可用; 表情随机在客户端
 * ({@link MaidEmojiChatBubbleRenderer} 子集随机), 与语音随机相互独立。
 * v79.20.4: 防刷屏 — 每女仆 5 秒 (100t) 最多发一次 (PersistentData 时间戳, 跨 session 自动失效)。
 */
public final class MaidEmojiApi {

    private MaidEmojiApi() {}

    /** 表情防刷屏间隔 (tick) — v79.20.4 用户裁定 "至少 5s 防刷屏" */
    public static final int EMOJI_THROTTLE_TICKS = 100;

    /** 时间戳键 (maid PersistentData 根) — 仅最后一次发送时间, 残留无害 (超时自动失效) */
    private static final String KEY_LAST_EMOJI_TICK = "lma_last_emoji_tick";

    /** 按目标类型发送表情 (对女仆=MAID / 对主人=OWNER), 服务端调用; 5s 内已发过 → 跳过 */
    public static void send(EntityMaid maid, MaidEmojiType type) {
        if (!(maid.level() instanceof ServerLevel sl)) {
            return;
        }
        // 防刷屏: 时间戳防残留 (0=未发过; last > now 时钟回退 → 视为过期)
        long now = sl.getGameTime();
        long last = maid.getPersistentData().getLong(KEY_LAST_EMOJI_TICK);
        if (last != 0 && now >= last && now - last < EMOJI_THROTTLE_TICKS) {
            return;
        }
        maid.getPersistentData().putLong(KEY_LAST_EMOJI_TICK, now);
        MaidChatBubblePacket.sendToTracking(maid, type);
    }

    /** 对女仆表情 (emoji_10/05/09 随机) */
    public static void sendMaidEmoji(EntityMaid maid) {
        send(maid, MaidEmojiType.MAID);
    }

    /** 对主人表情 (emoji_01/02/20-24x24 随机) */
    public static void sendOwnerEmoji(EntityMaid maid) {
        send(maid, MaidEmojiType.OWNER);
    }
}
