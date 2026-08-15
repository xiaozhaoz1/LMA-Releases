package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaInputRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.recipe.RecipeChain;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.*;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/** v79.45: 工作站基类 — GMPM 驱动, 节拍/计数/完成归 WorkStationPipeline */
public final class CraftChainPipeline extends WorkStationPipeline implements TaskConfigurable {

    @Override
    public String taskType() { return "craft_chain"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE); }

    @Override
    public List<TaskStep> steps() {
        return List.of(
            new TaskStep("resolve", "解析配方", StepType.COLLECT, List.of()),
            new TaskStep("gather", "收集材料", StepType.COLLECT, List.of("resolve")),
            new TaskStep("craft", "合成物品", StepType.CRAFT, List.of("gather")),
            new TaskStep("deliver", "交付产物", StepType.DELIVER, List.of("craft"))
        );
    }

    /** 纯验证 — 仅检查配方+材料(读操作)，不写日志 */
    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        // 目标解析 4 级回退 — validate/executeOne 共用 (错题 #182: 原 executeOne 只读 TASK_TARGET,
        // GUI 设置产物/默认产物配置时任务永不执行)
        String target = resolveTarget(maid, ctx.target());
        if (target.isEmpty()) return PipelineResult.failed("需要指定合成目标 (通过AI、默认产物或物品名称)");

        Map<Item, Integer> available = VanillaInputRegistry.readAllItems(maid);
        if (available.isEmpty()) return PipelineResult.failed("empty inventory");
        // 产物数量上限 (per-maid max_products 覆盖全局, -1=无限)
        CompoundTag cfg = pipelineConfig(maid);
        int maxProducts = cfg.contains("max_products")
                ? cfg.getInt("max_products")
                : ActiveTaskConfig.CRAFT_MAX_PRODUCTS.get();
        if (maxProducts > 0 && FlowTaskData.getCounter(maid) >= maxProducts)
            return PipelineResult.failed("已达产物上限 (" + maxProducts + ")");
        var chain = RecipeResolver.resolve(level, target, available);
        if (chain == null || chain.steps().isEmpty()) return PipelineResult.failed("no recipe for " + target);
        MaterialReport<Item> report = MaterialChecker.check(extractRequired(chain), available);
        if (!report.sufficient()) return PipelineResult.failed("insufficient materials");
        return PipelineResult.ok("craft_chain ready");
    }

    // ── 配置 GUI ──

    /** 自定义动作: 设置当前合成产物 (写入 TASK_TARGET) */
    public static final byte ACTION_SET_TARGET = 16;

    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        return TaskConfigGuiFactory.craftChainConfig(maid);
    }

    /** 配置同步: 当前产物 (per-maid cfg.target, 回退 TASK_TARGET) + 产物上限 */
    @Override
    public CompoundTag getConfigNbt(EntityMaid maid) {
        CompoundTag t = new CompoundTag();
        CompoundTag cfg = pipelineConfig(maid);
        String target = cfg.contains("target") ? cfg.getString("target") : TaskMetaData.getTarget(maid);
        t.putString("target", target);
        if (cfg.contains("max_products")) t.putInt("max_products", cfg.getInt("max_products"));
        return t;
    }

    /** 动作委托: 16=设置产物 (存 pipelineConfig target, 跨任务持久 — 不被 clearAll 清除); 其余走引擎通用动作 */
    @Override
    public boolean handleConfigAction(EntityMaid maid, byte action, CompoundTag payload) {
        if (action == ACTION_SET_TARGET) {
            String value = payload.getString("value").trim();
            if (value.isEmpty()) {
                pipelineConfig(maid).remove("target");
            } else {
                pipelineConfig(maid).putString("target", value);
            }
            return true;
        }
        return TaskConfigurable.super.handleConfigAction(maid, action, payload);
    }



    /** 一次工作单元 (原 execute 迁入; SUCCESS 计数链由基类 countSuccess 处理) */
    @Override
    protected TaskResult executeOne(ServerLevel w, EntityMaid m, BlockPos p) {
        // resolveTarget 与 validate 同源 (错题 #182) — GUI 设置产物/默认产物配置同样生效;
        // v79.61x execute 瘦身样本 3: 原 VanillaTasks.craft → CraftService 直调 (service.* 通配已覆盖)
        return CraftService.execute(w, m, p, resolveTarget(m, ""))
            ? TaskResult.SUCCESS : TaskResult.FAILED;
    }

    /**
     * 目标解析 — 4 级回退 (提交目标 → per-maid 产物 pipelineConfig.target → Cloth Config
     * 默认产物 → 运行期提交数据 TASK_TARGET)。validate/executeOne 共用, 单一事实源 (错题 #182)。
     */
    private String resolveTarget(EntityMaid maid, String submitTarget) {
        if (submitTarget != null && !submitTarget.isEmpty()) return submitTarget;
        String target = pipelineConfig(maid).getString("target");
        if (target.isEmpty()) target = ActiveTaskConfig.CRAFT_DEFAULT_PRODUCT.get();
        if (target.isEmpty()) target = maid.getPersistentData().getString(TaskKeys.TASK_TARGET);
        return target;
    }

    private static Map<Item, Integer> extractRequired(RecipeChain chain) {
        Map<Item, Integer> required = new LinkedHashMap<>();
        if (chain.cost() != null) required.putAll(chain.cost());
        return required;
    }
}
