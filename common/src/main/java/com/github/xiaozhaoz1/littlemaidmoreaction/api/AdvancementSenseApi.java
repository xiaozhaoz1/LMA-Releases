package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主人成就完成感知门面 — 外部 mod / 任务系统一行接入「玩家主人成就完成 → 女仆反应」。
 *
 * <p>监听入口在事件层: {@code AdvancementEarnEvent} (双平台) → {@code EnvSenseBroadcaster.onPlayerAdvancement}
 * → 本门面 {@link #fire}。本类不暴露 MC 双平台类型 ({@code Advancement}/{@code AdvancementHolder}),
 * 反应回调统一收到 String advancementId — 双平台差异全消化在事件监听层, API 跨平台零差异。
 *
 * <h3>用法 (完整易写)</h3>
 * <pre>
 * // ① 任意成就完成 → 反应 (通配, 默认行为)
 * AdvancementSenseApi.registerReaction((level, maid, advancementId) -> {
 *     MaidChatBubbleApi.showInfo(maid, "主人完成了成就: " + advancementId);
 * });
 *
 * // ② 精确指定成就 → 反应 (可与通配叠加, 指定+通配都会触发)
 * AdvancementSenseApi.registerReaction("minecraft:end/kill_dragon", (level, maid, advancementId) -> {
 *     // 特殊庆祝
 * });
 *
 * // ③ 任务系统主动触发 (可测/可手动调用; 未注册反应时无副作用)
 * AdvancementSenseApi.fire(level, maid, "minecraft:story/mine_stone");
 * </pre>
 *
 * <h3>设计裁定 (2026-08-31 用户)</h3>
 * <ul>
 *   <li>事件直连反应 — 不走 env: 信号 (不加 {@code Signals} 新常量, 不改信号通道)</li>
 *   <li>{@code registerReaction} 默认任意成就 (通配), 外部可精确指定 id — 通配+指定叠加触发</li>
 *   <li>反应先空着 (无内置默认反应) — 注册表为纯 Java 结构, 匹配逻辑可 JVM 单测 (错题 #174 铁律)</li>
 *   <li>门面只加不减 (api README 纪律, 外部 mod 契约)</li>
 * </ul>
 *
 * <h3>并发/安全</h3>
 * 反应在服务端 tick 线程执行; 回调 try/catch 包裹 — 第三方反应异常不炸服 (对齐
 * {@code dispatchToPipelines} 异常哨兵)。注册表用 CopyOnWrite 防事件与注册并发改。
 */
public final class AdvancementSenseApi {

    private static final Logger LOGGER = LoggerFactory.getLogger("LMA/AdvancementSense");

    /** 通配反应 — 任意成就完成都触发 */
    private static final List<AdvancementReaction> WILDCARD_REACTIONS = new ArrayList<>();

    /** 精确 id → 反应列表 (可注册多个) */
    private static final Map<String, List<AdvancementReaction>> SPECIFIC_REACTIONS = new HashMap<>();

    private AdvancementSenseApi() {}

    /**
     * 反应回调 — 主人在本服务端完成成就后, 对其契约女仆调用。
     *
     * <p>只暴露跨平台安全的 String id, 不暴露 {@code Advancement}/{@code AdvancementHolder};
     * 需要 MC 对象时用 {@code maid.level()} 取世界 (服务端 tick 上下文)。
     */
    @FunctionalInterface
    public interface AdvancementReaction {
        /**
         * 执行反应
         *
         * @param level         服务端世界 (女仆所在)
         * @param maid          主人契约女仆
         * @param advancementId 完成的成就 id (如 "minecraft:end/kill_dragon")
         */
        void react(ServerLevel level, EntityMaid maid, String advancementId);
    }

    /**
     * 注册通配反应 — 任意成就完成都触发 (默认行为)。
     */
    public static void registerReaction(AdvancementReaction reaction) {
        if (reaction == null) {
            throw new IllegalArgumentException("reaction must not be null");
        }
        synchronized (WILDCARD_REACTIONS) {
            WILDCARD_REACTIONS.add(reaction);
        }
    }

    /**
     * 注册精确 id 反应 — 仅当完成的成就 id 等于 {@code advancementId} 时触发。
     * 可与通配叠加 (指定+通配都会触发); 同一 id 可注册多个反应 (按注册顺序执行)。
     */
    public static void registerReaction(String advancementId, AdvancementReaction reaction) {
        if (advancementId == null || advancementId.isEmpty()) {
            throw new IllegalArgumentException("advancementId must not be null or empty");
        }
        if (reaction == null) {
            throw new IllegalArgumentException("reaction must not be null");
        }
        synchronized (SPECIFIC_REACTIONS) {
            SPECIFIC_REACTIONS.computeIfAbsent(advancementId, k -> new ArrayList<>()).add(reaction);
        }
    }

    /**
     * 触发反应 — 成就完成入口 (由事件监听层调用, 任务系统也可主动调用)。
     *
     * <p>执行顺序: 精确 id 反应 (注册顺序) → 通配反应 (注册顺序); 未注册任何反应时无副作用。
     * 每个回调独立 try/catch — 单个反应异常只记日志, 不中断后续反应, 不炸服。
     */
    public static void fire(ServerLevel level, EntityMaid maid, String advancementId) {
        // 只拦 id 空 (匹配必需); level/maid 透传给回调 (回调自行判空, safeReact 兜底) —
        // 保证纯 JVM 测试 (传 null) 与任务系统手动触发可用 (错题 #174 铁律)
        if (advancementId == null || advancementId.isEmpty()) {
            return;
        }
        // 精确 id 反应
        List<AdvancementReaction> specific;
        synchronized (SPECIFIC_REACTIONS) {
            specific = SPECIFIC_REACTIONS.get(advancementId);
        }
        if (specific != null) {
            for (AdvancementReaction r : new ArrayList<>(specific)) {
                safeReact(r, level, maid, advancementId);
            }
        }
        // 通配反应 (任意成就)
        List<AdvancementReaction> wildcard;
        synchronized (WILDCARD_REACTIONS) {
            wildcard = new ArrayList<>(WILDCARD_REACTIONS);
        }
        for (AdvancementReaction r : wildcard) {
            safeReact(r, level, maid, advancementId);
        }
    }

    /** 是否已注册任何反应 (测试/调试用) */
    public static boolean hasAnyReaction() {
        synchronized (SPECIFIC_REACTIONS) {
            if (!SPECIFIC_REACTIONS.isEmpty()) return true;
        }
        synchronized (WILDCARD_REACTIONS) {
            return !WILDCARD_REACTIONS.isEmpty();
        }
    }

    private static void safeReact(AdvancementReaction r, ServerLevel level, EntityMaid maid, String advancementId) {
        try {
            r.react(level, maid, advancementId);
        } catch (Exception e) {
            // maid/level 可能为 null (纯 JVM 测试/手动触发) — 日志不访问 null 参数
            LOGGER.warn("Advancement reaction failed for '{}' on maid {}", advancementId,
                    maid != null ? maid.getId() : "null", e);
        }
    }
}
