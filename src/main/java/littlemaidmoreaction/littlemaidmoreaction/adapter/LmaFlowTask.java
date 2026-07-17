package littlemaidmoreaction.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.List;

/**
 * TLM 代理任务 — 桥接 LMA 流程任务系统到 TLM Brain AI。
 *
 * <p>单个 IMaidTask 实例代表所有 LMA 流程任务类型。
 * createBrainTasks() 返回最小行为列表，避免与 LMA 规则引擎的
 * 导航/交互动作冲突。
 *
 * <h3>关键设计决策</h3>
 * <ul>
 *   <li>enableLookAndRandomWalk() = false — 防止脑内随机走动覆盖 WALK_TARGET</li>
 *   <li>enablePanic() = false — 任务期间不慌乱</li>
 *   <li>createBrainTasks() = 空列表 — 仅保留 MaidBrain 自动追加的基础行为
 *       (BegTask/WorkMealTask/StealEdible/UpdateActivity)</li>
 * </ul>
 */
public final class LmaFlowTask implements IMaidTask {

    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "flow_task");

    /** 保存原始 TLM 任务的 PersistentData key */
    static final String PREV_TASK_KEY = "lma_prev_tlm_task";

    // ── 单例 ──

    private static final LmaFlowTask INSTANCE = new LmaFlowTask();

    public static LmaFlowTask get() {
        return INSTANCE;
    }

    private LmaFlowTask() {}

    // ── IMaidTask ──

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.CRAFTING_TABLE.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // ★ v12.7 fix: TLM MaidBrain 对返回值做 .add() → 必须用可变列表
        return new java.util.ArrayList<>(List.of(Pair.of(4, new LmaFlowCoordinationBehavior())));
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return new java.util.ArrayList<>(List.of(Pair.of(4, new LmaFlowCoordinationBehavior())));
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        // ★ 关键：禁用随机走动，防止 brain 覆盖 LMA 动作设置的 WALK_TARGET
        return false;
    }

    @Override
    public boolean enablePanic(EntityMaid maid) {
        // 任务期间不慌乱 — 战斗由 LMA 规则引擎处理
        return false;
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return true;
    }

    @Override
    public boolean isHidden(EntityMaid maid) {
        // 不在 TLM 任务切换 GUI 中显示 — 由 LMA AI 工具分配
        return true;
    }

    @Override
    public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) {
        return FunctionCallSwitchResult.OK;
    }

    @Override
    public String getMaidActionSummary() {
        return "执行LMA流程任务（合成/祭坛/熔炉/炼药等）";
    }

    // ── 辅助方法 ──

    /**
     * 判断一个 IMaidTask 是否属于 LMA（通过 namespace 而非 instanceof）。
     * 同时覆盖 {@link LmaFlowTask} 和 {@link LmaTypedFlowTask}。
     */
    public static boolean isLmaTask(IMaidTask task) {
        return task != null && LittleMaidMoreAction.MOD_ID.equals(task.getUid().getNamespace());
    }

    /**
     * 保存当前 TLM 任务 UID 到 PersistentData，用于任务完成后恢复。
     */
    public static void savePreviousTask(EntityMaid maid) {
        IMaidTask current = maid.getTask();
        if (!isLmaTask(current)) {
            maid.getPersistentData().putString(PREV_TASK_KEY, current.getUid().toString());
        }
    }

    /**
     * 恢复之前保存的 TLM 任务。无保存值时恢复为 idle。
     */
    public static void restorePreviousTask(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        String prevUid = data.getString(PREV_TASK_KEY);
        data.remove(PREV_TASK_KEY);

        if (!prevUid.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(prevUid);
            if (rl != null) {
                com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager
                        .findTask(rl)
                        .ifPresent(prevTask -> {
                            if (isLmaTask(maid.getTask())) {
                                maid.setTask(prevTask);
                            }
                        });
                return;
            }
        }
        // 回退：恢复 idle
        if (isLmaTask(maid.getTask())) {
            maid.setTask(com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager.getIdleTask());
        }
    }

    /**
     * 读取当前 LMA 流程任务类型（从 PersistentData）。
     */
    public static String getCurrentFlowTaskType(EntityMaid maid) {
        return maid.getPersistentData().getString("lma_flow_task");
    }
}
