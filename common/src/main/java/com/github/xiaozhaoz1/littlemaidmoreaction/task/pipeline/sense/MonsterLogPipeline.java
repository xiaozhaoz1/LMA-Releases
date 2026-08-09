package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvScanner;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Set;

/**
 * v63: 怪物日志被动任务。
 *
 * <p>信号: MONSTER_NEARBY → 气泡报告附近怪物信息。
 * 信号: MONSTER_CLEAR → 气泡报告怪物清除。
 * 纯信号处理，不需要 tick。
 */
public final class MonsterLogPipeline implements TaskPipeline {

    @Override public String taskType() { return "monster_log"; }
    @Override public boolean isLongRunning() { return false; }
    @Override public boolean needsGameTick() { return false; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("", Set.of(Signals.ENV_MONSTER_NEARBY, Signals.ENV_MONSTER_CLEAR));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, String signal) {
        switch (signal) {
            case Signals.ENV_MONSTER_NEARBY -> {
                List<LivingEntity> monsters = snap.entities(EnvScanner.CAT_MONSTER);
                int count = monsters.size();
                if (count == 0) return;

                StringBuilder sb = new StringBuilder("⚠ 附近有");
                // 取前3种不同名称
                var names = monsters.stream()
                        .map(e -> e.getType().getDescription().getString())
                        .distinct().limit(3).toList();
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(names.get(i));
                }
                if (count > 3) sb.append(" 等");
                sb.append(" (").append(count).append("只)!");
                // v79.21: 规则触发气泡 (橙色 ⚠ + 5s 节流) — 前缀 ⚠ 由 API 补
                MaidChatBubbleApi.showTrigger(maid, sb.toString());

                var pd = pipelineData(maid);
                pd.putInt("monster_count", count);
                pd.putLong("last_seen", snap.gameTime());
            }
            case Signals.ENV_MONSTER_CLEAR -> {
                // v79.21: 完成气泡 (绿色 ✔) — 前缀 ✔ 由 API 补
                MaidChatBubbleApi.showComplete(maid, "怪物已清除");
                clearPipelineData(maid);
            }
        }
    }
    // onCleanup 用接口默认 (clearPipelineData) — v67.3 删除冗余覆写
}
