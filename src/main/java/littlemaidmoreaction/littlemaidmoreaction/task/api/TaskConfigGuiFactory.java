package littlemaidmoreaction.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.DefaultMaidTaskConfigContainer;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import littlemaidmoreaction.littlemaidmoreaction.task.gui.BellRingConfigMenu;
import littlemaidmoreaction.littlemaidmoreaction.task.gui.BlockInteractConfigMenu;
import littlemaidmoreaction.littlemaidmoreaction.task.gui.CraftChainConfigMenu;
import littlemaidmoreaction.littlemaidmoreaction.task.gui.ItemListConfigMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;

/**
 * 任务配置屏幕 API (v67.9) — 外部 mod / 任务注册方的配置 GUI 入口。
 *
 * <p>配合 {@link TaskPipeline#getConfigGuiProvider(EntityMaid)} 使用:
 * Pipeline 覆写该方法返回本类工厂创建的 MenuProvider, 即出现在
 * TLM 任务设置标签页 (女仆 GUI → 任务配置), 数据经
 * {@link TaskPipeline#pipelineConfig(EntityMaid)} (lma_cfg_&lt;taskType&gt; NBT)
 * 与引擎通用动作 ({@link TaskPipeline#handleConfigAction}) 读写。
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 1. 黑白名单配置 (furnace/jukebox/arm_transfer/collect_wood/collect_ore 内置):
 * {@literal @}Override public MenuProvider getConfigGuiProvider(EntityMaid maid) {
 *     return TaskConfigGuiFactory.itemListConfig(maid, "my_task");
 * }
 *
 * // 2. 自定义参数配置 — 自建 Menu + 工厂:
 * {@literal @}Override public MenuProvider getConfigGuiProvider(EntityMaid maid) {
 *     return TaskConfigGuiFactory.createMenuProvider(maid,
 *         Component.literal("我的任务配置"),
 *         (cid, inv, maidId) -> new MyConfigMenu(cid, inv, maidId));
 * }
 * </pre>
 *
 * <h3>内置工厂一览</h3>
 * <ul>
 *   <li>{@link #itemListConfig(EntityMaid, String)} — 通用黑白名单屏 (ItemListConfigMenu)</li>
 *   <li>{@link #blockInteractConfig(EntityMaid)} — 右键交互配置 (标记/定时器)</li>
 *   <li>{@link #craftChainConfig(EntityMaid)} — 配方链合成配置 (产物+上限)</li>
 *   <li>{@link #createMenuProvider(EntityMaid, Component, MenuFactory)} — 任意自定义 Menu 包装</li>
 *   <li>{@link #of(EntityMaid)} — TLM 桥接: 当前运行任务 → 配置 GUI (引擎内部用)</li>
 * </ul>
 */
public final class TaskConfigGuiFactory {

    private TaskConfigGuiFactory() {}

    // ── TLM 桥接 ──

    /**
     * 从当前运行的任务 Pipeline 获取配置 GUI (经 lma_flow_task 查当前任务)。
     * 无任务或 Pipeline 未覆写 getConfigGuiProvider 时回退 TLM 默认任务配置容器。
     *
     * <p>v67.10: 永不为 null — TLM 契约 {@code IMaidTask.getTaskConfigGuiProvider} 默认非 null,
     * {@code EntityMaid.openMaidGui(TASK_CONFIG)} 对 null 无防护 (NetworkHooks.openScreen),
     * 返回 null 会导致任务设置标签页点击没反应。
     * 供 {@code LmaTypedFlowTask.getTaskConfigGuiProvider()} 调用 (TLM 任务设置标签页入口)。
     */
    public static MenuProvider of(EntityMaid maid) {
        String taskType = FlowTaskData.getTask(maid);
        return forTask(maid, taskType);
    }

    /**
     * v67.12: 按 taskType 直查配置 GUI — 不依赖 lma_flow_task 写入时序。
     *
     * <p>任务刚选中即点「任务设置」时 lma_flow_task 尚未初始化 (v64 GUI_INIT 下 tick 写入),
     * of() 会误回退默认屏。TLM 任务实例自带 taskType, 直接按此查询,
     * 供 {@code LmaTypedFlowTask.getTaskConfigGuiProvider()} 等使用。
     */
    public static MenuProvider forTask(EntityMaid maid, String taskType) {
        TaskRegistry.TaskHandler h = taskType == null || taskType.isEmpty() ? null : TaskRegistry.get(taskType);
        MenuProvider provider = h == null ? null : h.pipeline().getConfigGuiProvider(maid);
        if (provider != null) {
            return provider;
        }
        // TLM 默认任务配置容器 (纯背包屏) — 与 TLM 自身任务行为一致
        return createMenuProvider(maid, Component.literal("任务配置"),
                (cid, inv, maidId) -> new DefaultMaidTaskConfigContainer(cid, inv, maidId));
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

    // ── 内置配置界面工厂 ──

    /**
     * 创建 BlockInteract 任务配置屏幕的 MenuProvider (标记/绑定、定时器间隔)。
     */
    public static MenuProvider blockInteractConfig(EntityMaid maid) {
        return createMenuProvider(maid, Component.literal("右键交互配置"),
            (cid, inv, maidId) -> new BlockInteractConfigMenu(cid, inv, maidId));
    }

    /**
     * v67.3: 通用黑白名单配置 (furnace/jukebox/arm_transfer 共用; v67.8 +collect_wood/collect_ore)。
     *
     * <p>标题用任务中文名 (lang key: task.littlemaidmoreaction.&lt;taskType&gt;)。
     * 名单存 pipelineConfig 的 blacklist/whitelist 键, 引擎经 ItemFilters.effective 读取 (per-maid 覆盖全局)。
     */
    public static MenuProvider itemListConfig(EntityMaid maid, String taskType) {
        return createMenuProvider(maid,
            Component.translatable("task.littlemaidmoreaction." + taskType),
            (cid, inv, maidId) -> new ItemListConfigMenu(cid, inv, maidId));
    }

    /**
     * v67.3: 配方链合成配置 (当前产物 + 产物上限)。
     */
    public static MenuProvider craftChainConfig(EntityMaid maid) {
        return createMenuProvider(maid,
            Component.translatable("task.littlemaidmoreaction.craft_chain"),
            (cid, inv, maidId) -> new CraftChainConfigMenu(cid, inv, maidId));
    }

    /**
     * v67.13: 敲钟单女仆间隔配置 (步进按钮 + 恢复全局)。
     * 间隔存 pipelineConfig "ring_interval", 空则用全局 BELL_RING_INTERVAL。
     */
    public static MenuProvider bellRingConfig(EntityMaid maid) {
        return createMenuProvider(maid,
            Component.translatable("task.littlemaidmoreaction.bell_ring"),
            (cid, inv, maidId) -> new BellRingConfigMenu(cid, inv, maidId));
    }
}
