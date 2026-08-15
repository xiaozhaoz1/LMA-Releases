package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 走到目标方块处工作的能力接口 (v79.61 基站重写 — 接口 default 承载走路四件套, 不强制继承;
 * maid_useful_task IMaidBlockDestroyTask 模式: default 承载算法, 叶子只填钩子)。
 *
 * <p>与 {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline} 组合使用:
 * {@link #taskType()} 与 TaskPipeline.taskType() 同签名 (一个方法满足两接口,
 * TaskConfigurable 同款模式)。数据键沿用 pipelineData (MaidData.pl)。
 *
 * <p>CSV 坐标表示沿用现状 (旧数据兼容); 导航语义 TLM BehaviorUtils 走路
 * (采集域 PathingApi 另走 — 语义不同不混用)。
 */
public interface BlockTargetNavigation {

    /** 目标坐标键名 (pipelineData) — CSV "x,y,z" */
    String KEY_TARGET = "target";

    /** 任务类型标识 — 与 TaskPipeline.taskType() 同签名 */
    String taskType();

    /** 目标坐标键名 — 可覆写 (Power 用 "pos") */
    default String targetKey() {
        return KEY_TARGET;
    }

    /** 临时数据 — 与 TaskConfigurable.pipelineData 同实现 (MaidData.pl, 免实现配置接口) */
    default CompoundTag pl(EntityMaid maid) {
        return MaidData.pl(maid, taskType());
    }

    /** 写目标坐标 (SEARCHING 找到目标后存) */
    default void writeTarget(EntityMaid maid, BlockPos target) {
        pl(maid).putString(targetKey(), target.toShortString());
    }

    /** 读目标坐标 — CSV 解析, 空/坏数据 → null (回 SEARCHING 重找) */
    default BlockPos readTarget(EntityMaid maid) {
        return parseTarget(pl(maid).getString(targetKey()));
    }

    /** CSV 坐标解析 (非 FSM 管线可复用, 如 RunningBelt) — 空/坏数据 → null */
    static BlockPos parseTarget(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            return new BlockPos(Integer.parseInt(p[0].trim()),
                    Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return null;
        }
    }

    /** 设置导航目标 (NavigationUtil 三件套 — 与旧内联逐行同义) */
    default void navigateTo(EntityMaid maid, BlockPos target) {
        NavigationUtil.navigateTo(maid, target);
    }

    /** 到达判定 — 距目标中心 < 3 格 (distSqr < 9.0); 可覆写阈值语义 */
    default boolean arrived(EntityMaid maid, BlockPos target) {
        return NavigationUtil.arrived(maid, target);
    }
}
