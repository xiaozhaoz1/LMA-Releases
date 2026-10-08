package com.github.xiaozhaoz1.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * 被动任务骨架 (v79.61 基站重写) — 接口 default 承载被动共用面:
 * isLongRunning 默认 false (纯信号无 tick 工作, 自终结; tick 型被动覆写 true),
 * validate 信号声明助手, 触发气泡 (showTrigger 内置 100t 节流)。
 * 不强制继承 — 被动管线 implements 本接口即用 (maid_useful_task 接口 default 模式)。
 */
public interface PassiveSignalSkeleton extends TaskPipeline, TaskSignalListener {

    /** 纯信号被动默认自终结 (无 tick 工作); tick 型被动覆写 true 获得心跳+看门狗 */
    @Override
    default boolean isLongRunning() {
        return false;
    }

    /** validate 信号声明助手 — PipelineResult.ok("", needsSignals) */
    default PipelineResult okSignals(Set<String> needsSignals) {
        return PipelineResult.ok("", needsSignals);
    }

    /** 触发气泡 — showTrigger (内置 100t 节流, 信号风暴天然去重) */
    default void bubbleTrigger(EntityMaid maid, String text) {
        MaidChatBubbleApi.showTrigger(maid, text);
    }
}
