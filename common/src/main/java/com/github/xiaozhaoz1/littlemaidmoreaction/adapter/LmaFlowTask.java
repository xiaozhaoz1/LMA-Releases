package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
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

    // PREV_TASK 恢复链路已删 (v79.54, 错题 #180): 写方全死 (savePreviousTask/saveAndSwitchTask 零调用),
    // 键恒空 → restorePreviousTask 恒走 idle 回退; 女仆 task 恢复由 TLM 原生 TASK_TAG 持久化
    // (EntityMaid.readAdditionalSaveData) + onEntityJoin FLOW_TASK 恢复双通道覆盖, 本链路冗余
}
