package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 每任务类型 IMaidTask — 每种 LMA 任务类型独立注册到 TLM TaskManager。
 *
 * <p>与 {@link LmaFlowTask}（泛用 fallback）的区别：
 * <ul>
 *   <li>UID: {@code lma:task/<taskType>}（而非 {@code lma:flow_task}）</li>
 *   <li>isHidden: false — 在 TLM GUI 中可见</li>
 *   <li>图标/名称因任务类型而异</li>
 * </ul>
 *
 * <p>行为与 {@link LmaFlowTask} 完全一致 (共享 {@link LmaFlowTaskBase})。
 */
public final class LmaTypedFlowTask extends LmaFlowTaskBase {

    private final ResourceLocation uid;
    private final String taskType;
    private final ItemStack icon;

    LmaTypedFlowTask(String taskType) {
        this.taskType = taskType;
        String safePath = TaskTypeUid.sanitize(taskType);
        this.uid = ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "task/" + safePath);
        this.icon = LmaTaskTypeRegistry.getIcon(taskType);
    }

    // ── IMaidTask ──

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public boolean isHidden(EntityMaid maid) {
        // 任务树 GUI "可视" 开关真实生效 (原恒 false — TaskToggle.isVisible 半成品修复);
        // /lma task 命令 + TaskTreeScreen 切换 → task_toggles.json → TLM 任务栏显示/隐藏
        return !com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle.isVisible(taskType);
    }

    /** 固定工作点标记 — 委托当前任务管线 (TLM 骑乘调度: 工作点任务不脱离坐骑) */
    @Override
    public boolean workPointTask(EntityMaid maid) {
        var h = com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry.get(taskType);
        return h != null && h.pipeline().workPointTask();
    }

    // 永不为 null — 工厂内部回退 TLM 默认配置容器 (TLM 契约)
    // 按实例 taskType 直查 — 任务刚选中即点设置时 lma_flow_task 尚未初始化, of() 会误回退默认屏
    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.forTask(maid, taskType);
    }

    @Override
    public java.util.List<com.mojang.datafixers.util.Pair<Integer, net.minecraft.world.entity.ai.behavior.BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // 2026-08-11c F2 (砍树管线检查): 连锁采集 (collect_wood/collect_ore) 不注册 Brain 导航 —
        // LMA 自导航 (PathingApi 直写 WALK_TARGET) 唯一化; 原 Brain (LmaFlowCoordinationBehavior,
        // MaidMoveToBlockTask) 在蓄力期间抢写 WALK_TARGET 拽向最近目标 (多树场景 = 别的树)
        // → 破块落空 (distSqr>9) 恶性循环 (无斧慢砍蓄力 40t×块数最严重)
        if ("collect_wood".equals(taskType) || "collect_ore".equals(taskType)) {
            return new java.util.ArrayList<>();
        }
        return super.createBrainTasks(maid);
    }

    @Override
    public String getMaidActionSummary() {
        return "执行LMA任务：" + taskType;
    }

    @Override
    public MutableComponent getName() {
        return Component.translatable("task." + LittleMaidMoreAction.MOD_ID + "." + TaskTypeUid.sanitize(taskType));
    }

    // ── 辅助 ──

    /** 获取原始 task_type 字符串 (如 "craft_chain") */
    public String taskType() {
        return taskType;
    }
}
