package com.github.xiaozhaoz1.littlemaidmoreaction.task.behavior;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.behavior.NearbyCollectBehavior;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.behavior.WorkEatBehavior;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;

/**
 * 自动注入默认行为 — Pipeline 写了 enableWorkEat/collectFilter 就自动加到 Brain.
 *
 * <p>注册: LittleMaidMoreActionExtension.addExtraMaidBrain()
 */
public enum DefaultBehaviorBrain implements IExtraMaidBrain {
    INSTANCE;

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getWorkBehaviors() {
        return List.of(
            Pair.of(3, new WorkEatBehavior()),
            Pair.of(4, new NearbyCollectBehavior(this::resolveFilter))
        );
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
        // v79.48: 自动修复 — core 所有 activity 都跑 (工作/战斗/发呆, 慢慢修); 优先级 5 低
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.REPAIR_AUTO_ENABLED.get()) {
            return List.of();
        }
        return List.of(Pair.of(5, new AutoRepairBehavior()));
    }

    /** 从当前任务的 Pipeline 获取收集过滤器 */
    private java.util.function.Predicate<net.minecraft.world.item.ItemStack> resolveFilter(EntityMaid maid) {
        if (!(maid.getTask() instanceof com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask)) return null;
        String uid = maid.getTask().getUid().getPath();
        String taskType = uid.startsWith("task/") ? uid.substring(5) : uid;
        var h = TaskRegistry.get(taskType);
        if (h == null) return null;
        // 配置维度拆分 — 未实现 TaskConfigurable 的管线无收集过滤
        return h.pipeline() instanceof com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable c
                ? c.collectFilter(maid) : null;
    }
}
