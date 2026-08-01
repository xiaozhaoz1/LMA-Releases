package littlemaidmoreaction.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.data.RetryPolicy;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSignal;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSnapshot;

import java.util.List;

/** 任务管道抽象 — 每个任务类型一个独立实现 */
public interface TaskPipeline {
    String taskType();

    /** v43.1: 纯验证 — 必须无副作用。默认返回 ok。 */
    default PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");
    }

    /** v35.2: 任务子步骤声明 (供任务树 GUI 展示) */
    default List<TaskStep> steps() { return List.of(); }

    /**
     * v43.2: 中断回调。v62: 默认委托 onCleanup()。
     */
    default void interrupt(EntityMaid maid) {
        onCleanup(maid);
    }

    /**
     * v44: 清理钩子。v62: 默认清除 pipelineData()。
     * 覆写时调 super.onCleanup() 或手动 clearPipelineData()。
     */
    default void onCleanup(EntityMaid maid) {
        clearPipelineData(maid);
    }

    /** v44: 长运行任务 — 需要调度器自动心跳 */
    default boolean isLongRunning() { return false; }

    /** v45: 失败/超时后重试策略 */
    default RetryPolicy retryPolicy() { return RetryPolicy.NEVER; }

    /** v52: 目标方块判断 — 供 Brain 导航匹配 */
    default boolean isTargetBlock(ServerLevel world, BlockPos pos, BlockState state, EntityMaid maid) { return false; }

    /**
     * 每游戏 tick 回调 — 由 {@link #needsGameTick()} 声明后经 TaskTickHandler 每 tick 驱动。
     * 与 {@link #isLongRunning()} 无关 (那是心跳/超时豁免维度)。
     */
    default void tick(ServerLevel world, EntityMaid maid) {}

    /** v53: 需要每游戏 tick (20/s) */
    default boolean needsGameTick() { return false; }

    /** 任务子步骤 */
    record TaskStep(String id, String label, StepType type, List<String> dependsOn) {}

    enum StepType { COLLECT, CRAFT, INTERACT, DELIVER, WAIT }

    // ── 默认行为 (覆写以启用) ──

    /** 工作时自动吃食物 */
    default boolean enableWorkEat() { return false; }

    /** 附近容器收集过滤: null=关闭, Predicate=开启 */
    @javax.annotation.Nullable
    default java.util.function.Predicate<net.minecraft.world.item.ItemStack> collectFilter(
            com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) { return null; }

    // ── 私有数据 (v62: 自动隔离, 自动清理) ──

    /**
     * 管线临时数据 — 存放执行进度 (计时器/槽位/步骤).
     * 键名 "lma_pl_&lt;taskType&gt;", 自动在 onCleanup() 时清除.
     */
    default CompoundTag pipelineData(EntityMaid maid) {
        String key = "lma_pl_" + taskType();
        CompoundTag d = maid.getPersistentData();
        CompoundTag data = d.getCompound(key);
        d.put(key, data);
        return data;
    }

    default void clearPipelineData(EntityMaid maid) {
        maid.getPersistentData().remove("lma_pl_" + taskType());
    }

    /**
     * 管线持久配置 — 跨任务保留 (材料锁定/配方缓存).
     * 键名 "lma_cfg_&lt;taskType&gt;", 不会被 onCleanup() 清除.
     */
    default CompoundTag pipelineConfig(EntityMaid maid) {
        String key = "lma_cfg_" + taskType();
        CompoundTag d = maid.getPersistentData();
        CompoundTag data = d.getCompound(key);
        d.put(key, data);
        return data;
    }

    default void clearPipelineConfig(EntityMaid maid) {
        maid.getPersistentData().remove("lma_cfg_" + taskType());
    }

    // ── 环境感知 (v63) ──

    /**
     * v63: 环境信号回调。EnvSenseBroadcaster 在全球广播命中匹配信号时调用。
     * 仅在 toggle 开启 且 validate().needsSignals() 包含该信号时触发。
     * 默认空实现 — 子类按需覆写。
     */
    default void onSignal(EntityMaid maid, EnvSnapshot snap, EnvSignal signal) {
        // 子类覆写
    }

    /**
     * v66: TLM 女仆界面「任务设置」标签 GUI。
     * 覆写返回 {@link net.minecraft.world.MenuProvider} 以提供自定义配置界面。
     * 返回 {@code null} 表示无配置界面（默认）。
     * <p>
     * 引擎通过 {@link littlemaidmoreaction.littlemaidmoreaction.task.api.TaskConfigGuiFactory#of TaskConfigGuiFactory.of(maid)}
     * 自动查找当前运行任务的 Pipeline 并调用此方法。
     */
    @javax.annotation.Nullable
    default net.minecraft.world.MenuProvider getConfigGuiProvider(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        return null;
    }

    /**
     * v67: 获取任务配置 NBT (供配置GUI数据同步)。
     *
     * <p>服务端调用，S→C 通过 {@code ReplyTaskConfigPacket} 发送给客户端。
     * 默认返回 {@link #pipelineConfig(EntityMaid)} — 配置即 pipelineConfig (key: lma_cfg_&lt;taskType&gt;)。
     * 需要自定义配置结构的任务再覆写。
     */
    default CompoundTag getConfigNbt(EntityMaid maid) {
        return pipelineConfig(maid);
    }

    /**
     * v67.1: 玩家按键触发回调 (C→S {@code InteractTriggerPacket} 引擎分发)。
     *
     * <p>客户端按键 → 引擎扫描玩家 10 格内 owned 女仆 → 对每个女仆按当前任务
     * 查 {@link TaskRegistry} → 调用本方法。默认 no-op — 支持按键触发的任务覆写。
     */
    default void onPlayerTrigger(EntityMaid maid, ServerPlayer player) {
        // 子类覆写
    }

    // ── 配置 GUI 通用动作 (C→S TaskConfigActionPacket) ──

    /** 引擎预留动作 0-15; 任务自定义动作建议从 16 起 */
    byte ACTION_TOGGLE = 0;    // payload: "key" — boolean 取反
    byte ACTION_SET_INT = 1;   // payload: "key" + "value" — int 赋值
    byte ACTION_REMOVE = 2;    // payload: "key" — 删除键
    byte ACTION_SET_LIST = 3;  // payload: "key" + "value" (逗号分隔) — 字符串列表整体覆盖 (v67.3)

    /**
     * v67.1: 处理配置 GUI 的动作委托 (C→S {@code TaskConfigActionPacket})。
     *
     * <p>引擎默认处理 4 个通用动作 (作用于 {@link #pipelineConfig(EntityMaid)}):
     * <ul>
     *   <li>{@link #ACTION_TOGGLE} — boolean 取反</li>
     *   <li>{@link #ACTION_SET_INT} — int 赋值 (payload "value")</li>
     *   <li>{@link #ACTION_REMOVE} — 删除 key</li>
     *   <li>{@link #ACTION_SET_LIST} — 字符串列表整体覆盖 (payload "value" 逗号分隔, 存 ListTag)</li>
     * </ul>
     * 任务自定义动作覆写本方法, 自定义常量建议从 {@code 16} 起, 未处理返回 false。
     *
     * @return true = 动作已处理
     */
    default boolean handleConfigAction(EntityMaid maid, byte action, CompoundTag payload) {
        CompoundTag cfg = pipelineConfig(maid);
        String key = payload.getString("key");
        switch (action) {
            case ACTION_TOGGLE -> cfg.putBoolean(key, !cfg.getBoolean(key));
            case ACTION_SET_INT -> cfg.putInt(key, payload.getInt("value"));
            case ACTION_REMOVE -> cfg.remove(key);
            case ACTION_SET_LIST -> {
                ListTag list = new ListTag();
                for (String s : payload.getString("value").split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) list.add(StringTag.valueOf(s));
                }
                cfg.put(key, list);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
