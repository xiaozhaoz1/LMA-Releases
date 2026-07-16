package littlemaidmoreaction.littlemaidmoreaction.core.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 规则执行追踪器 — v8.3: 条件/动作逐条追踪 + 实时游戏内消息。
 *
 * <p>v35.1: sendMsg 改为 BiConsumer 回调 — 移除 core/ 对 Player/Component 的直接引用。
 *
 * <p>启用 {@code /lmma_trace live} 后触发规则的玩家收到实时消息。
 * 环形缓冲区最多 200 条历史记录。
 */
public final class RuleTracer {

    private static final int MAX_HISTORY = 200;
    private static final List<TraceRecord> HISTORY = new ArrayList<>();
    private static volatile boolean enabled;
    private static volatile boolean liveMessages;
    private static final ThreadLocal<TraceRecord> CURRENT = new ThreadLocal<>();

    /** v35.1: 消息发送回调 (由 engine/ 层注入 Player.sendSystemMessage) */
    private static volatile BiConsumer<EntityMaid, String> msgSender;

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }
    public static void setLiveMessages(boolean v) { liveMessages = v; }
    public static boolean isLiveMessages() { return liveMessages; }
    public static TraceRecord current() { return CURRENT.get(); }

    /** v35.1: 注入消息发送器 (engine/ 层: Player::sendSystemMessage) */
    public static void setMessageSender(BiConsumer<EntityMaid, String> sender) { msgSender = sender; }

    /** 发送游戏内消息给女仆主人 */
    private static void sendMsg(EntityMaid maid, String msg) {
        if (!liveMessages || maid == null || msgSender == null) return;
        msgSender.accept(maid, msg);
    }

    /** 开始追踪 — 在规则命中时调用 */
    public static TraceRecord start(RuleDef rule, String eventId, EntityMaid maid) {
        if (!enabled) return TraceRecord.EMPTY;
        TraceRecord r = new TraceRecord(rule, eventId, Instant.now(), maid);
        CURRENT.set(r);
        synchronized (HISTORY) {
            HISTORY.add(0, r);
            if (HISTORY.size() > MAX_HISTORY) HISTORY.remove(HISTORY.size() - 1);
        }
        sendMsg(maid, "§6════ [LMA] 规则触发 ════");
        sendMsg(maid, "§e规则: §f" + rule.name() + " §8(id=" + rule.id() + ")");
        sendMsg(maid, "§e事件: §f" + eventId);
        sendMsg(maid, "§e条件(" + rule.conditions().size() + "): §7" + rule.matchMode());
        LittleMaidMoreAction.LOGGER.info("[LMA/Trace] START rule='{}' id={} event={}", rule.name(), rule.id(), eventId);
        return r;
    }

    /** 添加条件结果 */
    public static void addCondition(String key, boolean passed, String actual, String expected, String operator) {
        TraceRecord r = CURRENT.get();
        if (r == null || r.isEmpty()) return;
        r.conditions.add(new ConditionResult(key, passed, actual, expected, operator));
        String s = passed ? "§a✓" : "§c✗";
        String opStr = operator != null ? operator : "";
        sendMsg(r.maid, "  " + s + " §7" + key + " " + opStr + " " + expected + " §8→ " + actual);
    }

    /** 添加动作结果 */
    public static void addAction(int idx, String actionId, boolean success, String error) {
        TraceRecord r = CURRENT.get();
        if (r == null || r.isEmpty()) return;
        r.actions.add(new ActionResult(actionId, idx, success, error));
        String s = success ? "§aOK" : ("§cFAIL§8 " + (error != null ? error : ""));
        sendMsg(r.maid, "  §7[" + (idx + 1) + "] §f" + actionId + " §8→ " + s);
    }

    public static List<TraceRecord> history() {
        synchronized (HISTORY) { return List.copyOf(HISTORY); }
    }

    public static void clear() {
        synchronized (HISTORY) { HISTORY.clear(); }
        CURRENT.remove();
    }

    public static void finish() {
        TraceRecord r = CURRENT.get();
        if (r != null && !r.isEmpty()) {
            if (r.timestamp != null)
                r.durationMs = Duration.between(r.timestamp, Instant.now()).toMillis();
            sendMsg(r.maid, "§6════ 追踪完成 §7(" + r.durationMs + "ms) §8条件" + r.conditions.size() + "/动作" + r.actions.size());
            LittleMaidMoreAction.LOGGER.info("[LMA/Trace] FINISH rule='{}' dur={}ms cond={} act={}",
                r.ruleName, r.durationMs, r.conditions.size(), r.actions.size());
        }
        CURRENT.remove();
    }

    // ── 数据类 ──

    public static class TraceRecord {
        static final TraceRecord EMPTY = new TraceRecord(null, null, null, null);
        public final Integer ruleId;
        public final String ruleName;
        public final String eventId;
        public final Instant timestamp;
        public final List<ConditionResult> conditions = new ArrayList<>();
        public final List<ActionResult> actions = new ArrayList<>();
        public final transient EntityMaid maid;
        public boolean matched;
        public long durationMs;

        TraceRecord(RuleDef rule, String eventId, Instant ts, EntityMaid maid) {
            this.ruleId = rule != null ? rule.id() : null;
            this.ruleName = rule != null ? rule.name() : null;
            this.eventId = eventId;
            this.timestamp = ts;
            this.maid = maid;
        }
        public boolean isEmpty() { return ruleId == null; }
    }

    public record ConditionResult(String key, boolean passed, String actual, String expected, String operator) {}
    public record ActionResult(String actionId, int stepIndex, boolean success, String error) {}
}
