package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 任务气泡语义门面 (v79.21) — adapter 层, 把任务领域语义 (友好名/状态中文)
 * 映射到低层 {@link MaidChatBubbleApi}。所有实现 (气泡数据构造/节流) 在 chatbubble 包。
 *
 * <p>用法：
 * <pre>
 *   LmaTaskProgressDisplay.showTaskStart(maid, "craft_chain");
 *   LmaTaskProgressDisplay.showStep(maid, "crank", "NAVIGATING");   // 状态转换时
 *   LmaTaskProgressDisplay.showComplete(maid, "craft_chain", 3, 10);
 *   LmaTaskProgressDisplay.showFail(maid, "craft_chain", "目标不可达");
 *   LmaTaskProgressDisplay.showNoContent(maid, "craft_chain");
 * </pre>
 *
 * <p>步骤气泡走进度气泡 (替换式) — 循环状态 (搬运每物品 TO_TAKE↔TO_DEPOSIT,
 * 装配 STRIKE↔EAT_RESET) 只保留最新状态, 不堆积。
 */
public final class LmaTaskProgressDisplay {

    private LmaTaskProgressDisplay() {}

    // ── 公开 API (委托 MaidChatBubbleApi) ──

    /** 任务开始气泡 */
    public static void showTaskStart(EntityMaid maid, String taskType) {
        MaidChatBubbleApi.showInfo(maid, "开始执行: " + friendlyName(taskType));
    }

    /**
     * 状态步骤气泡 — 替换式进度气泡 (状态名 → 中文, 未映射回退原文)。
     *
     * @param state 状态枚举名 (state.name(), 如 "SEARCHING")
     */
    public static void showStep(EntityMaid maid, String taskType, String state) {
        MaidChatBubbleApi.showProgress(maid, friendlyName(taskType) + " — " + stateName(state), 0d);
    }

    /** 任务完成气泡 (绿色 ✔) — maxCount &gt; 0 时附计数 (count/max) */
    public static void showComplete(EntityMaid maid, String taskType, long count, long maxCount) {
        String msg = maxCount > 0
                ? "任务完成: " + friendlyName(taskType) + " (" + count + "/" + maxCount + ")"
                : "任务完成: " + friendlyName(taskType);
        MaidChatBubbleApi.showComplete(maid, msg);
    }

    /** 任务失败气泡 (红色 ✘, 30秒节流) */
    public static void showFail(EntityMaid maid, String taskType, String reason) {
        String msg = "任务失败: " + friendlyName(taskType);
        if (reason != null && !reason.isEmpty()) {
            msg += ": " + reason;
        }
        MaidChatBubbleApi.showFail(maid, msg);
    }

    /** 复杂任务无内容 — 气泡 + 主人聊天栏消息 (保留 v40 既有行为) */
    public static void showNoContent(EntityMaid maid, String taskType) {
        String msg = "我不知道要" + verbFor(taskType) + "什么，请让主人告诉我";
        MaidChatBubbleApi.showInfo(maid, msg);
        if (maid.getOwner() instanceof ServerPlayer player) {
            Component name = maid.getName();
            player.sendSystemMessage(
                Component.literal("<").append(name).append("> ").append(msg)
            );
        }
    }

    // ── 辅助 (package-private 供 JVM 测试) ──

    /** 任务类型 → 友好中文名 */
    static String friendlyName(String taskType) {
        if (taskType == null || taskType.isEmpty()) return "未知任务";
        return switch (taskType) {
            case "craft_chain"  -> "配方链合成";
            case "furnace"      -> "熔炉烧炼";
            case "brewing"      -> "炼药";
            case "bell_ring"    -> "敲钟";
            case "jukebox"      -> "唱片机";
            case "arm_transfer" -> "搬运";
            case "crank"        -> "手摇曲柄";
            case "power"        -> "动力齿轮";
            case "press"        -> "女仆冲压";
            case "mix"          -> "女仆搅拌";
            case "chain_wood"   -> "连锁砍树";
            case "chain_ore"    -> "连锁挖矿";
            case "maid_assembly" -> "便携装配";
            case "block_interact" -> "方块交互";
            case "monster_log"  -> "怪物日志";
            default -> taskType;
        };
    }

    /** FSM 状态枚举名 → 中文 (v79.21; 未映射回退原文) */
    static String stateName(String state) {
        if (state == null || state.isEmpty()) return "工作中";
        return switch (state) {
            case "SEARCHING"   -> "寻找目标";
            case "NAVIGATING"  -> "前往途中";
            case "CHOPPING"    -> "砍伐中";
            case "CRANKING"    -> "摇动曲柄";
            case "WORKING"     -> "工作中";
            case "POWERING"    -> "提供动力";
            case "TO_TAKE"     -> "前往取物";
            case "TAKING"      -> "取物中";
            case "TO_DEPOSIT"  -> "前往存放";
            case "DEPOSITING"  -> "存放中";
            case "IDLE"        -> "待机";
            case "TRY_START"   -> "尝试启动";
            case "ADVANCE"     -> "推进";
            case "STRIKE"      -> "敲击中";
            case "EAT_RESET"   -> "进食复位";
            case "WAITING"     -> "等待";
            case "MOVE"        -> "移动";
            case "LOOK"        -> "注视";
            default -> state;
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
