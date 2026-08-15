package com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.ProgressChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * v79.21: 女仆聊天气泡通用 API (任务进度/完成/规则触发) — 所有实现的落点。
 *
 * <p>服务端直调 TLM {@link ChatBubbleManager} (SynchedEntityData 自动同步客户端渲染,
 * 无需网络包)。三类气泡:
 * <ul>
 *   <li>进度气泡 — TLM {@link ProgressChatBubbleData} 进度条, <b>替换式</b> (每女仆仅保留
 *       最新进度气泡, 防 TLM 5 气泡上限堆积, 见 {@link ChatBubbleManager#removeChatBubble})</li>
 *   <li>完成气泡 — 绿色 §a✔, 无节流 (任务生命周期天然限频)</li>
 *   <li>失败气泡 — 红色 §c✘, 30 秒 (600t) 节流 — 沿用 v67.3 TaskDispatcher 既有节流语义,
 *       顺带补上超时气泡缺失的节流 (错题: 同类刷屏 bug)</li>
 *   <li>触发气泡 — 橙色 §e⚠, 5 秒 (100t) 节流 — 防信号 (如怪物日志) 刷屏</li>
 * </ul>
 *
 * <p>节流键: maid PersistentData {@code lma_bubble_fail_tick} / {@code lma_bubble_trigger_tick}
 * (long 覆盖写, 闭环; 0=未发, last &gt; now 时钟回退视为过期 — 逐字镜像 {@link MaidEmojiApi})。
 *
 * <p>任务语义 (友好名/状态中文) 在 adapter 层 {@code LmaTaskProgressDisplay} —
 * 本类只接收最终文案, 供任意管线/开发者使用。
 */
public final class MaidChatBubbleApi {

    private MaidChatBubbleApi() {}

    // ── 常量 ──

    /** 完成/失败/触发/信息气泡持续时间 (8秒) */
    public static final int INFO_TICK = 160;

    /** 进度气泡持续时间 (5秒, 替换式刷新) */
    public static final int PROGRESS_TICK = 100;

    /** 失败气泡节流 (tick, 30秒) — 沿用 TaskDispatcher.FAIL_BUBBLE_INTERVAL */
    public static final int FAIL_THROTTLE_TICKS = 600;

    /** 触发气泡节流 (tick, 5秒) — 镜像 MaidEmojiApi.EMOJI_THROTTLE_TICKS 防刷屏 */
    public static final int TRIGGER_THROTTLE_TICKS = 100;

    /** 进度条背景/前景色 */
    private static final int BAR_BG = 0xFF333333;
    private static final int BAR_FG = 0xFF4CAF50;

    /** 失败气泡节流键 (maid PersistentData 根) — 仅最后一次时间戳, 残留无害 (超时自动失效) */
    private static final String KEY_LAST_FAIL_TICK = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.BUBBLE_FAIL_TICK;

    /** 触发气泡节流键 — 同上 */
    private static final String KEY_LAST_TRIGGER_TICK = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.BUBBLE_TRIGGER_TICK;

    /** 进度气泡替换跟踪 — 每女仆仅保留最新进度气泡 id (弱引用, 实体卸载自动回收) */
    private static final java.util.Map<EntityMaid, Long> LAST_PROGRESS_BUBBLE =
            new java.util.WeakHashMap<>();

    // ── 公开 API ──

    /** 普通信息气泡 (无节流) — 8秒 */
    public static void showInfo(EntityMaid maid, String msg) {
        showInfo(maid, msg, INFO_TICK);
    }

    /** 普通信息气泡 (自定义持续时间) — 无节流 */
    public static void showInfo(EntityMaid maid, String msg, int duration) {
        add(maid, TextChatBubbleData.create(duration, Component.literal(msg),
                IChatBubbleData.TYPE_2, IChatBubbleData.DEFAULT_PRIORITY));
    }

    /** 任务完成气泡 — 绿色 §a✔ 前缀, 8秒, 无节流 */
    public static void showComplete(EntityMaid maid, String msg) {
        showInfo(maid, "§a✔ " + msg);
    }

    /** 任务失败气泡 — 红色 §c✘ 前缀, 30秒节流 */
    public static void showFail(EntityMaid maid, String msg) {
        if (!throttled(maid, KEY_LAST_FAIL_TICK, FAIL_THROTTLE_TICKS)) {
            showInfo(maid, "§c✘ " + msg);
        }
    }

    /** 规则触发气泡 — 橙色 §e⚠ 前缀, 5秒节流 (防信号刷屏) */
    public static void showTrigger(EntityMaid maid, String msg) {
        if (!throttled(maid, KEY_LAST_TRIGGER_TICK, TRIGGER_THROTTLE_TICKS)) {
            showInfo(maid, "§e⚠ " + msg);
        }
    }

    /**
     * 任务进度气泡 — TLM 进度条 + 替换式刷新 (先移除旧进度气泡再添加)。
     *
     * @param progress01 进度 0.0~1.0 (渲染端 clamp; 无百分比概念传 0)
     * @return 气泡 key (可用于手动清除); 非服务端返回 -1
     */
    public static long showProgress(EntityMaid maid, String msg, double progress01) {
        if (!(maid.level() instanceof ServerLevel)) {
            return -1;
        }
        // progress<=0 无进度概念 → 文本气泡 (实测黑条: TLM ProgressChatBubbleData
        // progress=0 渲染黑底空进度条 BAR_BG 0xFF333333)
        if (progress01 <= 0) {
            showInfo(maid, msg);
            return -1;
        }
        ChatBubbleManager cbm = maid.getChatBubbleManager();
        Long prev = LAST_PROGRESS_BUBBLE.get(maid);
        if (prev != null && prev >= 0) {
            // 已过期气泡 remove 为空操作
            cbm.removeChatBubble(prev);
        }
        long key = cbm.addChatBubble(ProgressChatBubbleData.create(
                PROGRESS_TICK, IChatBubbleData.TYPE_2, IChatBubbleData.DEFAULT_PRIORITY,
                Component.literal(msg), BAR_BG, BAR_FG, progress01, false));
        LAST_PROGRESS_BUBBLE.put(maid, key);
        return key;
    }

    // ── 节流 (纯函数, JVM 可测) ──

    /**
     * 节流判定 — 0=未发过 / 时钟回退 (last &gt; now) 视为过期可发。
     * 逐字镜像 {@link MaidEmojiApi} 语义 (时间戳防残留)。
     */
    static boolean shouldThrottle(long last, long now, int interval) {
        return last != 0 && now >= last && now - last < interval;
    }

    /** 节流检查 + 放行时写时间戳 — 被节流时不动时间戳 (窗口从最近一次放行算起) */
    private static boolean throttled(EntityMaid maid, String key, int interval) {
        if (!(maid.level() instanceof ServerLevel sl)) {
            return true;
        }
        long now = sl.getGameTime();
        long last = maid.getPersistentData().getLong(key);
        if (shouldThrottle(last, now, interval)) {
            return true;
        }
        maid.getPersistentData().putLong(key, now);
        return false;
    }

    private static void add(EntityMaid maid, IChatBubbleData bubble) {
        if (maid.level() instanceof ServerLevel) {
            maid.getChatBubbleManager().addChatBubble(bubble);
        }
    }
}
