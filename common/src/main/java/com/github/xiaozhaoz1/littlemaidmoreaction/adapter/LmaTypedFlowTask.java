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
        String safePath = sanitize(taskType);
        this.uid = ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "task/" + safePath);
        this.icon = LmaTaskTypeRegistry.getIcon(taskType);
    }

    /** 将 task_type 转为 ResourceLocation path 合法字符 */
    private static String sanitize(String raw) {
        String s = raw.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_\\-./]", "_");
        if (s.isEmpty()) s = "unknown";
        return s;
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
        // 在 TLM GUI 中可见 — 玩家能直观看到女仆当前任务类型
        return false;
    }

    // 永不为 null — 工厂内部回退 TLM 默认配置容器 (TLM 契约)
    // 按实例 taskType 直查 — 任务刚选中即点设置时 lma_flow_task 尚未初始化, of() 会误回退默认屏
    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.forTask(maid, taskType);
    }

    @Override
    public String getMaidActionSummary() {
        return "执行LMA任务：" + taskType;
    }

    @Override
    public MutableComponent getName() {
        return Component.translatable("task." + LittleMaidMoreAction.MOD_ID + "." + sanitize(taskType));
    }

    // ── 辅助 ──

    /** 获取原始 task_type 字符串 (如 "craft_chain") */
    public String taskType() {
        return taskType;
    }
}
