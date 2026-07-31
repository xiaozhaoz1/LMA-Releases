package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;

/**
 * TLM → LMA 任务配置 GUI 桥接入口。
 *
 * <h3>TLM 桥接</h3>
 * {@link #of(EntityMaid)} 查找当前运行任务的 Pipeline，调用其 getConfigGuiProvider 返回配置界面。
 * 供 {@code LmaTypedFlowTask.getTaskConfigGuiProvider()} 调用。
 *
 * <h3>通用 MenuProvider 工厂</h3>
 * {@link #createMenuProvider(EntityMaid, Component, MenuFactory)} 创建标准 MenuProvider，
 * 供 Pipeline 的 getConfigGuiProvider 覆写使用。
 *
 * <h3>任务专属配置界面</h3>
 * 需要自定义参数的任务在此处添加工厂方法，如 {@link #blockInteractConfig(EntityMaid)}。
 * Pipeline 的 getConfigGuiProvider 中调用对应工厂方法。
 */
public final class TaskConfigGui {

    private TaskConfigGui() {}

    // ── TLM 桥接 ──

    /**
     * 从当前运行的任务 Pipeline 获取配置 GUI。
     * 如果无任务或 Pipeline 未覆写 getConfigGuiProvider，返回 {@code null}。
     */
    @Nullable
    public static MenuProvider of(EntityMaid maid) {
        String taskType = FlowTaskData.getTask(maid);
        if (taskType.isEmpty()) return null;
        TaskRegistry.TaskHandler h = TaskRegistry.get(taskType);
        if (h == null) return null;
        return h.pipeline().getConfigGuiProvider(maid);
    }

    // ── 通用 MenuProvider 工厂 ──

    /**
     * 容器工厂函数 — 供 {@link #createMenuProvider} 使用。
     */
    @FunctionalInterface
    public interface MenuFactory {
        AbstractContainerMenu create(int containerId, Inventory playerInv, int maidId);
    }

    /**
     * 创建标准 TLM 兼容的 MenuProvider。
     *
     * <p>使用示例 (在 Pipeline 的 getConfigGuiProvider 中):
     * <pre>
     * public MenuProvider getConfigGuiProvider(EntityMaid maid) {
     *     return TaskConfigGui.createMenuProvider(maid,
     *         Component.literal("我的任务配置"),
     *         (cid, inv, maidId) -> new MyConfigMenu(cid, inv, maidId));
     * }
     * </pre>
     *
     * @param maid 女仆
     * @param displayName GUI 标题
     * @param menuFactory 容器工厂
     */
    public static MenuProvider createMenuProvider(EntityMaid maid, Component displayName,
                                                   MenuFactory menuFactory) {
        int maidId = maid.getId();
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return displayName;
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
                return menuFactory.create(containerId, playerInv, maidId);
            }
        };
    }

    // ── 任务专属配置界面工厂 ──

    /**
     * 创建 BlockInteract 任务配置屏幕的 MenuProvider。
     *
     * <p>使用方式 — Pipeline 的 getConfigGuiProvider 中:
     * <pre>
     * public MenuProvider getConfigGuiProvider(EntityMaid maid) {
     *     return TaskConfigGui.blockInteractConfig(maid);
     * }
     * </pre>
     */
    public static MenuProvider blockInteractConfig(EntityMaid maid) {
        return createMenuProvider(maid, Component.literal("右键交互配置"),
            (cid, inv, maidId) -> new BlockInteractConfigMenu(cid, inv, maidId));
    }
}
