package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * AI 工具门控骨架 (v79.61 基站重写) — 接口 default 承载 trigger 门控:
 * 世界修改类工具统一 AI 操控开关 (AiControlGate) 判定, 不再各自覆写
 * (maid_storage_manager AbstractTool 简化版; 只读工具保持裸 ITool 不门控)。
 */
public interface GatedMaidTool<R> extends ITool<R> {

    /** 门控 — AI 操控任务开启才可被 LLM 调用 */
    @Override
    default boolean trigger(EntityMaid maid,
                            com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion chatCompletion) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate.isEnabled(maid);
    }
}
