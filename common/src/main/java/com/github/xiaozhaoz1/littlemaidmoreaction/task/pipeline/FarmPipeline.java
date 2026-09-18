package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.FarmExecute;
import net.minecraft.server.level.ServerLevel;

/**
 * 作物区域种植管线 (v79.62 用户裁定区域制) — 女仆在绑定的框选区域里:
 * 收全部成熟作物 (左键毁/右键收按区域设定) + 种区域指定作物 (跨区块续).
 *
 * <p>区域存储: 该女仆 NBT {@code lma_cfg_farm.regions} ({@link FarmRegionStorage} — v79.64 起随实体走,
 * 不再用全局 config 文件; 每区域记维度, 女仆换维度后该区域暂停);
 * 区域注册 = 木棒选区 + 区域管理 GUI (MaidListScreen 入口).
 *
 * <p>无绑定区域 → validate failed (任务不启动, 空转提示).
 */
public final class FarmPipeline implements TaskPipeline, TaskConfigurable {

    @Override public String taskType() { return "farm"; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        if (!FarmRegionStorage.hasAny(maid)) {
            return PipelineResult.failed("未绑定种植区域 (木棒框选 → 女仆界面管理)");
        }
        // 种田固定工作范围 — home 模式由 farm 运行期自动切换 (用户裁定: 开 farm 自动 HOME 模式), 不前置拦
        return PipelineResult.ok("");
    }

    /** 每 tick 直执行 — FarmExecute (自动 home + 区域内收+种推进) */
    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        FarmExecute.tick(world, maid);
    }

    /** 终结: 清导航/进度守卫 (路径残留闭环; 不碰 home 开关 — TLM home 激活作息调度会干扰 LMA 任务) */
    @Override
    public void onCleanup(EntityMaid maid) {
        com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
    }
}
