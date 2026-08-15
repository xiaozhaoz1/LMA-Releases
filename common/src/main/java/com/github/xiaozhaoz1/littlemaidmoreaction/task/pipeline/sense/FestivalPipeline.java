package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.PassiveSignalSkeleton;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.FestivalTable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.util.Set;

/**
 * v79.47: 节日气泡被动任务 — FESTIVAL_ENTER 状态广播 → 节日文案气泡 (showTrigger 100t 节流)。
 *
 * <p>消费端 per-maid 当天首收去重: PD 存上次触发日期 (EpochDay long — 完整日期含年份),
 * 同天静默, 跨天/跨年 (EpochDay 不同) 再触发; 每天覆盖天然闭环。
 * <b>坑 (用户裁定)</b>: 去重键禁存 month/day — 明年同日月日相同 → 误判同天 → 永久静默。
 * stateless 广播 (Broadcaster 每轮查表非空即发) 配合: 女仆错过广播后上线首收即触发。
 */
public final class FestivalPipeline implements PassiveSignalSkeleton {

    /** 去重键: 上次触发日期 (EpochDay long) — 每天覆盖, 无残留 (v79.55 收编 TaskKeys) */
    static final String LAST_ANNOUNCE_KEY = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.FESTIVAL_DAY;

    /** 去重判定纯函数 — 上次触发日 != 今天 → 应触发 (跨年: EpochDay 不同自然重新触发) */
    static boolean shouldAnnounce(long lastEpochDay, LocalDate today) {
        return lastEpochDay != today.toEpochDay();
    }

    @Override public String taskType() { return "festival"; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return okSignals(Set.of(Signals.ENV_FESTIVAL_ENTER));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, String signal) {
        if (!Signals.ENV_FESTIVAL_ENTER.equals(signal)) return;
        LocalDate today = LocalDate.now();
        FestivalTable.Festival f = FestivalTable.lookup(today);
        if (f == null) return;  // 表空/被清 — 无节日可报

        // 当天首收去重 (EpochDay 完整日期; 同天静默, 跨天/跨年再触发)
        long stored = maid.getPersistentData().getLong(LAST_ANNOUNCE_KEY);
        if (!shouldAnnounce(stored, today)) return;

        String msg = f.text() == null || f.text().isEmpty() ? f.name() : f.text();
        bubbleTrigger(maid, msg);
        maid.getPersistentData().putLong(LAST_ANNOUNCE_KEY, today.toEpochDay());
    }
}
