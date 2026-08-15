package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.SlotLayout;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.FurnaceService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 熔炉管道 — 查烧炼配方 → 检查原材料 → 执行。
 * v67.2/v67.3: 黑白名单过滤 (Cloth Config furnace.blacklist/whitelist, per-maid 可覆盖)。
 * v79.45: 工作站基类 — GMPM 驱动, 节拍/计数/完成归 WorkStationPipeline。
 * v79.59: 名单匹配改 ItemFilters.isAllowed(Item) (错题 #191) — Item.toString() 双平台语义不一致。
 * v79.61x 架构审计 A: 配方扫描/原料解析/生效名单抽至 {@link FurnaceService}
 * (原 validate 与 resolveSmeltIngredient 两处内联重复), 管线只留判定。
 * v79.61x execute 瘦身: 原 FurnaceExecute 相位机 (收产物→加料→加燃料) 收编进管线。
 * v79.61x S1: 相位机迁移进 {@link TaskStateMachine} (方案 A) — 原 int 状态键
 * DataKey.FURNACE_PHASE (lma_furnace_phase) 退役, 状态入 FSM 内存态 (lma_pl_furnace.fsm);
 * 节拍/到达门用 workStationGated() 复用 WorkStationPipeline.gate; 计数复用 countSuccess。
 */
public final class FurnacePipeline extends TaskStateMachine<FurnacePipeline.Phase> implements TaskConfigurable {

    /** 工作单元相位 (原 FurnaceExecute.Phase 收编) — 状态随 FSM 内存态跨拍持久 */
    enum Phase { COLLECT_RESULT, ADD_INPUT, ADD_FUEL }

    @Override protected Class<Phase> stateClass() { return Phase.class; }
    @Override protected Phase initialState() { return Phase.COLLECT_RESULT; }
    @Override public String taskType() { return "furnace"; }
    /** 固定工作点任务 (工作站交互) — TLM 骑乘中不脱离坐骑 */
    @Override public boolean workPointTask() { return true; }
    /** 工作站式门 (到达 + 节拍 30t) — 对齐原 WorkStationPipeline 语义 */
    @Override protected boolean workStationGated() { return true; }
    @Override public int executeInterval() { return 30; }

    @Override
    protected Map<Phase, Set<Phase>> transitions() {
        return Map.of(
                Phase.COLLECT_RESULT, Set.of(Phase.ADD_INPUT),
                Phase.ADD_INPUT,      Set.of(Phase.ADD_FUEL),
                Phase.ADD_FUEL,       Set.of(Phase.COLLECT_RESULT)
        );
    }

    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return w.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("smelt", "熔炉烧炼", StepType.CRAFT, List.of())); }

    /** 黑白名单配置 GUI (per-maid) */
    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "furnace");
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String reason = FurnaceService.validateSmelt(level, maid, ctx.target());
        return reason == null ? PipelineResult.ok("") : PipelineResult.failed(reason);
    }

    /**
     * 每拍轮转 收产物→加料→加燃料 (原 FurnaceExecute.execute 相位机语义)。
     * 原料解析失败 → 气泡失败, 不计数不转相 (原 executeOne 返回 FAILED 语义);
     * 无熔炉 → 仍计数 (原语义: meaningful=false 被调用方忽略, 仍 SUCCESS)。
     */
    @Override
    protected Phase tick(Phase phase, ServerLevel world, EntityMaid maid) {
        BlockPos target = gateTarget(maid);
        String ingredientKey = FurnaceService.resolveSmeltIngredient(world, maid);
        if (ingredientKey.isEmpty()) {
            LittleMaidMoreAction.LOGGER.debug("[LMA/Furnace] no ingredient, stay {}", phase);
            com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi.showFail(maid, "furnace 失败");
            return null;
        }
        if (!(world.getBlockEntity(target) instanceof AbstractFurnaceBlockEntity furnace)) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/Furnace] no furnace at {}", target.toShortString());
            WorkStationPipeline.countSuccess(maid); // 原语义: 无熔炉时仍计数
            return null;
        }

        Phase next;
        boolean meaningful;
        switch (phase) {
            case COLLECT_RESULT -> {
                next = Phase.ADD_INPUT;
                meaningful = FurnaceService.collectResult(furnace, maid, SlotLayout.FURNACE);
            }
            case ADD_INPUT -> {
                next = Phase.ADD_FUEL;
                meaningful = FurnaceService.addInput(furnace, maid, ingredientKey, SlotLayout.FURNACE);
            }
            default -> { // ADD_FUEL
                next = Phase.COLLECT_RESULT;
                meaningful = FurnaceService.addFuel(furnace, maid, ingredientKey, SlotLayout.FURNACE);
            }
        }
        LittleMaidMoreAction.LOGGER.debug("[LMA/Furnace] phase={} meaningful={} input={}", phase, meaningful, ingredientKey);
        WorkStationPipeline.countSuccess(maid);
        return next;
    }

    /** 工作站门已保证 TARGET_POS 存在 — 读目标坐标 (到达后) */
    private BlockPos gateTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get())
                .get().currentBlockPosition();
    }
}
