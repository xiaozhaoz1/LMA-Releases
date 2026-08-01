package littlemaidmoreaction.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.task.runtime.TaskDispatcher;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvScanner;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSignal;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSnapshot;
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
        return PipelineResult.ok("", Set.of(EnvSignal.MONSTER_NEARBY, EnvSignal.MONSTER_CLEAR));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, EnvSignal signal) {
        switch (signal) {
            case MONSTER_NEARBY -> {
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
                maid.getChatBubbleManager().addTextChatBubble(sb.toString());

                var pd = pipelineData(maid);
                pd.putInt("monster_count", count);
                pd.putLong("last_seen", snap.gameTime());
            }
            case MONSTER_CLEAR -> {
                maid.getChatBubbleManager().addTextChatBubble("✔ 怪物已清除");
                clearPipelineData(maid);
            }
        }
    }
    // onCleanup 用接口默认 (clearPipelineData) — v67.3 删除冗余覆写
}
