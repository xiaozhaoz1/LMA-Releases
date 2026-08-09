package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.io.IExecutor;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.VanillaTasks;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.*;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.ProgressNotifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 唱片机管道 (Phase 2) — 扫描唱片 → 写规则 → JukeboxInteractAction 处理导航+弹出入碟。
 */
public final class JukeboxPipeline implements TaskPipeline {

    @Override public String taskType() { return "jukebox"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.minecraft.world.level.block.Blocks.JUKEBOX); }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("play", "播放唱片", StepType.INTERACT, List.of())); }

    /** v67.3: 唱片黑白名单配置 GUI (per-maid) */
    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "jukebox");
    }

    /** v44: 纯验证 — 仅扫描背包是否有唱片(读操作)，不写日志/通知 */
    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        // v67.3: 唱片黑白名单 (per-maid 覆盖全局)
        var cfg = pipelineConfig(maid);
        var black = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_BLACKLIST),
                ActiveTaskConfig.JUKEBOX_BLACKLIST.get());
        var white = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_WHITELIST),
                ActiveTaskConfig.JUKEBOX_WHITELIST.get());
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
//? if 1.20.1 {
            if (!s.isEmpty() && s.is(ItemTags.MUSIC_DISCS) && ItemFilters.isAllowed(s, black, white)) {
//?} else {
            if (!s.isEmpty() && s.is(ItemTags.CREEPER_DROP_MUSIC_DISCS) && ItemFilters.isAllowed(s, black, white)) {
//?}
                if (target.isEmpty()) return PipelineResult.ok("");
                if (s.getDescriptionId().contains(target) || s.getItem().toString().contains(target))
                    return PipelineResult.ok("");
            }
        }
        return PipelineResult.failed(ProgressNotifier.NO_DISC);
    }

    public static IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                VanillaTasks.jukebox(w, m, p, TaskMetaData.getTarget(m));
                return TaskResult.CONTINUE;
            }
        };
    }

}
