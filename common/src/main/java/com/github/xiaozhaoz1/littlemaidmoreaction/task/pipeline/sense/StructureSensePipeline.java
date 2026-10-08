package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.PassiveSignalSkeleton;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;

/**
 * v79.47: 结构感知气泡被动任务 — 附近结构动态信号 → 气泡。
 *
 * <p>v79.58: 检测端 scanAllStructures 一次遍历所有已生成结构, 差集 → 动态 String 信号 —
 * 通配订阅本类; 气泡文案含方位。
 *
 * <p>v79.61 (用户裁定重设计): 4 信号后缀 discover/refresh/enter/leave —
 * discover/refresh 查缓存气泡 (≤enter 格 "附近有X" / 远 "{方向}方向有X");
 * enter/leave 静默 (信号已消费, 留未来 LLM 上下文)。文案 emit 侧按玩家位置
 * 算好存缓存 (女仆共享零计算), onSignal 零重扫零计算。
 *
 * <p>站立点结构信号 (STRUCTURE_ENTER/LEAVE — 脚下进任何结构都响, 信息价值低且频繁)
 * <b>不气泡</b> (用户裁定分层: 气泡层砍, 信号层保留供未来内部管线消费)。
 */
public final class StructureSensePipeline implements PassiveSignalSkeleton {

    /** 结构信号前缀 (与 StructureSense 同源 — 通配订阅) */
    private static final String PREFIX = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.StructureSense.PREFIX;


    @Override public String taskType() { return "structure_sense"; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        // 通配订阅 — 结构动态信号 (v79.58: 全量结构 id 不可穷举, 前缀匹配由 dispatch 支持)
        return okSignals(Set.of(PREFIX + "*"));
    }

    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, String signal) {
        if (!signal.startsWith(PREFIX)) return;
        // 信号 id = PREFIX + "{registryId}:{kind}" — 后缀截取 (registryId 含 ':' 用 lastIndexOf 保完整)
        String rest = signal.substring(PREFIX.length());
        int cut = rest.lastIndexOf(':');
        if (cut < 0) return;
        String structId = rest.substring(0, cut);
        String kind = rest.substring(cut + 1);

        // v79.61: enter/leave 静默 (信号已消费, 留未来 LLM 上下文); 仅 discover/refresh 气泡
        if (!kind.equals("discover") && !kind.equals("refresh")) return;

        UUID owner = maid.getOwnerUUID();
        if (owner == null) return;  // 无主女仆 — 结构信号只发主人女仆 (v79.60 裁定)
        // 文案 emit 侧按玩家位置算好存缓存 (女仆共享零计算, 零重扫)
        String text = com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.StructureSense.textFor(owner, structId, kind);
        if (text == null) return;  // 未映射结构/无缓存 — 忽略气泡 (信号已消费)

        // showTrigger: 100t 节流内置 — 信号风暴 (结构节流 1200t/边沿) 天然去重
        bubbleTrigger(maid, text);
    }
}
