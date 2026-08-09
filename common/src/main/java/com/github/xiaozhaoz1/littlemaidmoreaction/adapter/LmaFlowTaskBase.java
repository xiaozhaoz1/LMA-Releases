package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;

/**
 * LMA 流程任务共享基类 — {@link LmaFlowTask} 与 {@link LmaTypedFlowTask} 的公共 TLM 契约。
 *
 * <p>共享约定:
 * <ul>
 *   <li>createBrainTasks() — 挂 LMA 协调行为 (可变列表, TLM MaidBrain 对返回值做 .add())</li>
 *   <li>enableLookAndRandomWalk() = true — 禁用随机闲逛, 防止覆盖 LMA 设置的 WALK_TARGET</li>
 *   <li>enablePanic() = false — 任务期间不慌乱 (战斗由 LMA 处理)</li>
 *   <li>isEnable() = true — 始终可用</li>
 *   <li>onFunctionCallSwitch() = OK — AI 调用切换放行</li>
 * </ul>
 */
public abstract class LmaFlowTaskBase implements IMaidTask {

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // TLM MaidBrain 对返回值做 .add() → 必须用可变列表
        return new java.util.ArrayList<>(List.of(Pair.of(4, new LmaFlowCoordinationBehavior())));
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return new java.util.ArrayList<>(List.of(Pair.of(4, new LmaFlowCoordinationBehavior())));
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        // true=禁用随机闲逛 — 防止覆盖 LMA 设置的 WALK_TARGET
        return true;
    }

    @Override
    public boolean enablePanic(EntityMaid maid) {
        // 任务期间不慌乱 — 战斗由 LMA 规则引擎处理
        return false;
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return true;
    }

    @Override
    public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) {
        return FunctionCallSwitchResult.OK;
    }
}
