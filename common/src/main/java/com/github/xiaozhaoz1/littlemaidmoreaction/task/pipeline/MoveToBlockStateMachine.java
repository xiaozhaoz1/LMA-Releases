package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;

/**
 * 走到目标方块处工作的状态机基座 (v79.61 架构批 3b C2 → 基站重写: 薄壳化)。
 *
 * <p>走路能力 (writeTarget/readTarget/navigateTo/arrived/parseTarget/targetKey)
 * 已下沉 {@link BlockTargetNavigation} 接口 default — 本类只保留模板职责:
 * FSM 引擎 + cleanup 清导航。新任务可不继承本类, 直接
 * {@code implements TaskPipeline, BlockTargetNavigation} 即用走路四件套。
 */
public abstract class MoveToBlockStateMachine<S extends Enum<S>> extends TaskStateMachine<S>
        implements BlockTargetNavigation {

    protected MoveToBlockStateMachine() {}

    @Override
    protected void cleanup(EntityMaid maid) {
        super.cleanup(maid);
        NavigationMemory.clearAllNav(maid);
    }
}
