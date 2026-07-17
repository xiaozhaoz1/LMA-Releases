package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 任务进度聊天气泡 — 在女仆头顶显示任务执行状态。
 *
 * <p>用法：
 * <pre>
 *   LmaTaskProgressDisplay.showProgress(maid, "合成木棍中...");
 *   LmaTaskProgressDisplay.showComplete(maid, "altar_craft", 3, 10);
 *   LmaTaskProgressDisplay.showNoContent(maid, "craft_chain");
 * </pre>
 *
 * <p>气泡持续时间 5 秒 (100 tick)，使用 TLM TYPE_2 背景。
 */
public final class LmaTaskProgressDisplay {

    /** 任务进度气泡持续时间 (5秒) */
    private static final int PROGRESS_TICK = 100;

    /** 完成/信息气泡持续时间 (8秒) */
    private static final int INFO_TICK = 160;

    private LmaTaskProgressDisplay() {}

    // ── 公开 API ──

    /** 显示任务进度文本 */
    public static void showProgress(EntityMaid maid, String message) {
        ChatBubbleManager cbm = maid.getChatBubbleManager();
        var bubble = TextChatBubbleData.create(
            PROGRESS_TICK,
            Component.literal(message),
            com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData.TYPE_2,
            com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData.DEFAULT_PRIORITY
        );
        cbm.addChatBubble(bubble);
    }

    /** 显示任务开始 */
    public static void showTaskStart(EntityMaid maid, String taskType) {
        showProgress(maid, "开始执行: " + friendlyName(taskType));
    }

    /** 显示步骤进度 */
    public static void showStepStart(EntityMaid maid, String taskType, int step) {
        showProgress(maid, friendlyName(taskType) + " — 步骤 " + (step + 1));
    }

    /** 显示任务完成 (有限次循环) */
    public static void showComplete(EntityMaid maid, String taskType, int count, int maxCount) {
        String msg;
        if (maxCount > 0) {
            msg = "任务完成: " + friendlyName(taskType) + " (" + count + "/" + maxCount + ")";
        } else {
            msg = "任务完成: " + friendlyName(taskType);
        }
        ChatBubbleManager cbm = maid.getChatBubbleManager();
        var bubble = TextChatBubbleData.create(
            INFO_TICK,
            Component.literal(msg),
            com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData.TYPE_2,
            com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData.DEFAULT_PRIORITY
        );
        cbm.addChatBubble(bubble);
    }

    /** 复杂任务无内容 — 告诉玩家需要 AI 指定 */
    public static void showNoContent(EntityMaid maid, String taskType) {
        String msg = "我不知道要" + verbFor(taskType) + "什么，请让主人告诉我";
        ChatBubbleManager cbm = maid.getChatBubbleManager();
        var bubble = TextChatBubbleData.create(
            INFO_TICK,
            Component.literal(msg),
            com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData.TYPE_2,
            com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData.DEFAULT_PRIORITY
        );
        cbm.addChatBubble(bubble);

        // 同时给主人在线时发送聊天栏消息
        if (maid.getOwner() instanceof ServerPlayer player) {
            Component name = maid.getName();
            player.sendSystemMessage(
                Component.literal("<").append(name).append("> ").append(msg)
            );
        }
    }

    // ── 辅助 ──

    /** 任务类型 → 友好中文名 */
    private static String friendlyName(String taskType) {
        if (taskType == null) return "未知任务";
        return switch (taskType) {
            case "craft_chain"  -> "配方链合成";
            case "furnace"      -> "熔炉烧炼";
            case "brewing"      -> "炼药";
            case "bell_ring"    -> "敲钟";
            case "jukebox"      -> "唱片机";
            default -> taskType;
        };
    }

    /** 任务类型 → 动词 */
    private static String verbFor(String taskType) {
        if (taskType == null) return "做";
        return switch (taskType) {
            case "craft_chain" -> "合成";
            case "furnace", "brewing" -> "烧炼";
            case "bell_ring" -> "敲";
            default -> "做";
        };
    }
}
