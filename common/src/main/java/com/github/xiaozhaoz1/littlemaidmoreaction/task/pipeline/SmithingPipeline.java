package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Blocks;

/**
 * v79.62.1 锻造管线 — 女仆找锻造台; TLM 任务设置打开原版锻造台样式容器 (无模板升级).
 * <p>配置界面 = {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.MaidSmithingMenu} 容器
 * (base+addition+result + 女仆背包 + 玩家背包); 合成逻辑在容器服务端 (跳过模板, 数据驱动配方).
 */
public final class SmithingPipeline implements TaskPipeline, TaskConfigurable {

    @Override public String taskType() { return "smithing"; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        if (findSmithingTable(level, maid) == null) return PipelineResult.failed("附近没有锻造台");
        return PipelineResult.ok("");
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {}

    @Override
    public void onCleanup(EntityMaid maid) {}

    @Override
    public MenuProvider getConfigGuiProvider(EntityMaid maid) {
        // TLM 任务设置标签页 → 原版锻造台样式容器 (无模板升级)
        return TaskConfigGuiFactory.createMenuProvider(maid,
            net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.smithing"),
            (cid, inv, maidId) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.MaidSmithingMenu(cid, inv, maid));
    }

    public static BlockPos findSmithingTable(ServerLevel level, EntityMaid maid) {
        BlockPos center = maid.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (level.getBlockState(p).is(Blocks.SMITHING_TABLE)) return p.immutable();
        }
        return null;
    }
}
