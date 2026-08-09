package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * TLM 代理任务 — 桥接 LMA 流程任务系统到 TLM Brain AI。
 *
 * <p>单个 IMaidTask 实例代表所有 LMA 流程任务类型 (fallback)。
 * 共享 TLM 契约见 {@link LmaFlowTaskBase}。
 */
public final class LmaFlowTask extends LmaFlowTaskBase {

    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "flow_task");

    /** 保存原始 TLM 任务的 PersistentData key */

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
    public boolean isHidden(EntityMaid maid) {
        // 不在 TLM 任务切换 GUI 中显示 — 由 LMA AI 工具分配
        return true;
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
            maid.getPersistentData().putString(TaskKeys.PREV_TASK, current.getUid().toString());
        }
    }

    /**
     * 恢复之前保存的 TLM 任务。无保存值时恢复为 idle。
     */
    public static void restorePreviousTask(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        String prevUid = data.getString(TaskKeys.PREV_TASK);
        data.remove(TaskKeys.PREV_TASK);

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
        return maid.getPersistentData().getString(TaskKeys.FLOW_TASK);
    }
}
