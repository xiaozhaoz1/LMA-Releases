package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.api.input.StickBindAccess;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.input.StickBindProvider;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskTypeUid;

/**
 * {@link StickBindProvider} 的实现 (2026-09-21 接口倒置) — 装配层。
 *
 * <p>职责: 把两处原本被 vanilla 直接依赖的能力**收拢到一处**并注入:
 * <ul>
 *   <li>两个配置值 ← {@code config.ActiveTaskConfig.BI_MARK_ITEM / BI_BIND_ITEM}</li>
 *   <li>任务类型纯函数 ← {@code task.api.TaskTypeUid.extractTaskType} (无 MC 依赖, 纯 JVM 可测)</li>
 * </ul>
 *
 * <p><b>为什么住 init/</b>: 这是"把下层能力接起来"的装配动作 (init = 注册与初始化),
 * 不是业务实现; 且 init 已允许 import config 与 task/api (实测: init/README 依赖方向 = 原版注册 API + config + api)。
 */
public final class LmaTaskBinding implements StickBindProvider {

    /** 注入到 api 侧访问点 (由 {@code LmaRegistrar.init()} 调用; 幂等) */
    public static void install() {
        StickBindAccess.install(new LmaTaskBinding());
    }

    @Override
    public String markItemId() {
        return ActiveTaskConfig.BI_MARK_ITEM.get();
    }

    @Override
    public String bindItemId() {
        return ActiveTaskConfig.BI_BIND_ITEM.get();
    }

    @Override
    public String taskTypeOf(String uidPath) {
        return TaskTypeUid.extractTaskType(uidPath);
    }
}
