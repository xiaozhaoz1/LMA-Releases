package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveTask;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.StructureSense;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;

/**
 * 结构感知气泡被动 (v79.61x 脱管线迁移 — 原 StructureSensePipeline, 零 tick 纯触发型) —
 * 附近结构动态信号 → 气泡。
 *
 * <p>v79.61 用户裁定重设计: 4 信号后缀 discover/refresh/enter/leave —
 * discover/refresh 查缓存气泡 (≤enter 格 "附近有X" / 远 "{方向}方向有X");
 * enter 气泡 "到X啦" (v79.6x 用户裁定: IN 不再全静默), leave 静默
 * (信号已消费, 留未来 LLM 上下文)。文案 emit 侧按玩家位置
 * 算好存缓存 (女仆共享零计算), trigger 零重扫零计算。
 *
 * <p>站立点结构信号 (STRUCTURE_ENTER/LEAVE — 脚下进任何结构都响, 信息价值低且频繁)
 * <b>不气泡</b> (用户裁定分层: 气泡层砍, 信号层保留供未来内部管线消费)。
 */
public final class StructureSensePassiveTask implements PassiveTask {

    /** 结构信号前缀 (与 StructureSense 同源 — 通配订阅) */
    private static final String PREFIX = StructureSense.PREFIX;

    @Override public String taskType() { return "structure_sense"; }

    @Override public int cooldown() { return 0; }

    @Override
    public Set<String> needsSignals() {
        // 通配订阅 — 结构动态信号 (v79.58: 全量结构 id 不可穷举, 前缀匹配由 dispatch 支持)
        return Set.of(PREFIX + "*");
    }

    @Override
    public void trigger(ServerLevel level, EntityMaid maid, String signalId) {
        if (!signalId.startsWith(PREFIX)) return;
        // 信号 id = PREFIX + "{registryId}:{kind}" — 后缀截取 (registryId 含 ':' 用 lastIndexOf 保完整)
        String rest = signalId.substring(PREFIX.length());
        int cut = rest.lastIndexOf(':');
        if (cut < 0) return;
        String structId = rest.substring(0, cut);
        String kind = rest.substring(cut + 1);

        // v79.6x: discover/refresh/enter 气泡 (enter "到X啦" — 用户裁定 IN 不再全静默); leave 静默 (留未来 LLM 上下文)
        if (!kind.equals("discover") && !kind.equals("refresh") && !kind.equals("enter")) return;

        UUID owner = maid.getOwnerUUID();
        if (owner == null) return;  // 无主女仆 — 结构信号只发主人女仆 (v79.60 裁定)
        // 文案 emit 侧按玩家位置/朝向算好存缓存 (女仆共享零计算, 零重扫); Component 客户端双语 v79.6x
        var text = StructureSense.textFor(owner, structId, kind);
        if (text == null) return;  // 未映射结构/无缓存 — 忽略气泡 (信号已消费)

        // showTrigger: 100t 节流内置 — 信号风暴 (结构节流 1200t/边沿) 天然去重
        MaidChatBubbleApi.showTrigger(maid, text);
    }
}
