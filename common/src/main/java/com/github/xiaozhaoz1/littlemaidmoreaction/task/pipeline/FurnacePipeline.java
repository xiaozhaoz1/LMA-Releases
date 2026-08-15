package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.SlotLayout;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.DataKey;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.FurnaceService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 熔炉管道 — 查烧炼配方 → 检查原材料 → 执行。
 * v67.2/v67.3: 黑白名单过滤 (Cloth Config furnace.blacklist/whitelist, per-maid 可覆盖)。
 * v79.45: 工作站基类 — GMPM 驱动, 节拍/计数/完成归 WorkStationPipeline。
 * v79.59: 名单匹配改 ItemFilters.isAllowed(Item) (错题 #191) — Item.toString() 双平台语义不一致。
 * v79.61x 架构审计 A: 配方扫描/原料解析/生效名单抽至 {@link FurnaceService}
 * (原 validate 与 resolveSmeltIngredient 两处内联重复), 管线只留判定。
 * v79.61x execute 瘦身: 原 FurnaceExecute 相位机 (收产物→加料→加燃料) 收编进管线 —
 * 状态键 TaskKeys.FURNACE_PHASE 原样 (行为零变化), 单拍业务动作 = {@link FurnaceService} (收产物/加料/加燃料);
 * FurnaceExecute 层删除 (五层尺: 状态进管线 / 单拍留原语 / execute 吸收)。
 */
public final class FurnacePipeline extends WorkStationPipeline implements TaskConfigurable {

    @Override public String taskType() { return "furnace"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return w.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("smelt", "熔炉烧炼", StepType.CRAFT, List.of())); }

    /** 黑白名单配置 GUI (per-maid) */
    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "furnace");
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String reason = FurnaceService.validateSmelt(level, maid, ctx.target());
        return reason == null ? PipelineResult.ok("") : PipelineResult.failed(reason);
    }

    /** 一次工作单元 (SUCCESS 计数链由基类 countSuccess 处理; TASK_INPUT 死写已删 #181)。
     *  相位机 (原 FurnaceExecute): 每拍轮转 收产物→加料→加燃料; 相位存 TaskKeys.FURNACE_PHASE (原键原语义)。 */
    @Override
    protected TaskResult executeOne(ServerLevel w, EntityMaid m, BlockPos p) {
        String ingredientKey = FurnaceService.resolveSmeltIngredient(w, m);
        if (ingredientKey.isEmpty()) return TaskResult.FAILED;
        if (!(w.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity furnace)) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/Furnace] no furnace at {}", p.toShortString());
            return TaskResult.SUCCESS; // 原语义: 无熔炉时 meaningful=false 被调用方忽略, 仍计数
        }
        CompoundTag data = m.getPersistentData();
        int ord = MaidData.get(m, DataKey.FURNACE_PHASE);
        Phase phase = ord < 0 || ord >= Phase.VALUES.length ? Phase.COLLECT_RESULT : Phase.VALUES[ord];
        boolean meaningful = switch (phase) {
            case COLLECT_RESULT -> {
                data.putInt(TaskKeys.FURNACE_PHASE, Phase.ADD_INPUT.ordinal());
                yield FurnaceService.collectResult(furnace, m, SlotLayout.FURNACE);
            }
            case ADD_INPUT -> {
                data.putInt(TaskKeys.FURNACE_PHASE, Phase.ADD_FUEL.ordinal());
                yield FurnaceService.addInput(furnace, m, ingredientKey, SlotLayout.FURNACE);
            }
            case ADD_FUEL -> {
                data.putInt(TaskKeys.FURNACE_PHASE, Phase.COLLECT_RESULT.ordinal());
                yield FurnaceService.addFuel(furnace, m, ingredientKey, SlotLayout.FURNACE);
            }
        };
        LittleMaidMoreAction.LOGGER.debug("[LMA/Furnace] phase={} meaningful={} input={}", phase, meaningful, ingredientKey);
        TaskStateManager.heartbeat(m, w.getGameTime());
        return TaskResult.SUCCESS;
    }

    /** 工作单元相位 (原 FurnaceExecute.Phase 收编) — 状态随任务键跨拍持久 */
    private enum Phase {
        COLLECT_RESULT, ADD_INPUT, ADD_FUEL;
        private static final Phase[] VALUES = values();
    }
}
