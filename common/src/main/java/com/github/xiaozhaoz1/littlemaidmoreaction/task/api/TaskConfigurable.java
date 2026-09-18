package com.github.xiaozhaoz1.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import javax.annotation.Nullable;

/**
 * 任务配置维度接口 (v79.28 接口瘦身 — 从 TaskPipeline 拆分)。
 *
 * <p>管线按需实现本接口获得: 配置 GUI (TLM 任务设置标签页) / 配置 NBT 读写
 * ({@code lma_cfg_<taskType>} 持久跨任务) / 通用配置动作 (C→S 网络包委托) /
 * 临时数据 ({@code lma_pl_<taskType>}, onCleanup 自动清理) / 行为配置 (工作进食/收集过滤)。
 *
 * <p>与 {@link TaskPipeline} 组合使用: 实现类 implements TaskPipeline, TaskConfigurable —
 * {@link #taskType()} 与 {@link TaskPipeline#taskType()} 同签名, 一个方法同时满足两接口,
 * 数据键命名 ({@code lma_pl_<type>} / {@code lma_cfg_<type>}) 与任务类型一致。
 *
 * <p>新管线不实现 = 无配置面 (默认行为), 零样板。
 */
public interface TaskConfigurable {

    // ── 配置 GUI 通用动作 (C→S TaskConfigActionPacket) ──

    /** 引擎预留动作 0-15; 任务自定义动作建议从 16 起 */
        /**
     * per-maid 开关键 ({@code pipelineConfig} 内) — 被动任务的"启用/禁用"由它承载,
     * 缺省 false (默认关, 由子任务界面 {@code PassiveToggleConfigScreen} 显式开启)。
     *
     * <p>v79.63: 由裸字面量收敛为常量 (键名 = 跨层契约; 屏写入 / {@code PassiveConfigUtil} 读取必须同源)。
     */
    String KEY_ENABLED = "enabled";

    byte ACTION_TOGGLE = 0;    // payload: "key" — boolean 取反
    byte ACTION_SET_INT = 1;   // payload: "key" + "value" — int 赋值
    byte ACTION_REMOVE = 2;    // payload: "key" — 删除键
    byte ACTION_SET_LIST = 3;  // payload: "key" + "value" (逗号分隔) — 字符串列表整体覆盖
    byte ACTION_SET_STRING = 4; // payload: "key" + "value" — 字符串赋值

    // ── 任务类型 (与 TaskPipeline.taskType() 同签名, 实现类一个方法满足两接口) ──

    /** 任务类型标识 — 数据键命名 (lma_pl_&lt;type&gt; / lma_cfg_&lt;type&gt;) 唯一真相 */
    String taskType();

    // ── 配置 GUI ──

    /**
     * TLM 女仆界面「任务设置」标签 GUI。
     * 覆写返回 {@link net.minecraft.world.MenuProvider} 以提供自定义配置界面。
     * 返回 {@code null} 表示无配置界面（默认）。
     * <p>
     * 引擎通过 {@link TaskConfigGuiFactory#of TaskConfigGuiFactory.of(maid)}
     * 自动查找当前运行任务的 Pipeline 并调用此方法。
     */
    @Nullable
    default net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return null;
    }

    /**
     * 获取任务配置 NBT (供配置GUI数据同步)。
     *
     * <p>服务端调用，S→C 通过 {@code ReplyTaskConfigPacket} 发送给客户端。
     * 默认返回 {@link #pipelineConfig(EntityMaid)} — 配置即 pipelineConfig (key: lma_cfg_&lt;taskType&gt;)。
     * 需要自定义配置结构的任务再覆写。
     */
    default CompoundTag getConfigNbt(EntityMaid maid) {
        return pipelineConfig(maid);
    }

    /**
     * 处理配置 GUI 的动作委托 (C→S {@code TaskConfigActionPacket})。
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
            case ACTION_SET_STRING -> cfg.putString(key, payload.getString("value"));
            default -> {
                return false;
            }
        }
        return true;
    }

    // ── 私有数据 (自动隔离, 自动清理; PL 内存态 — tick 零 NBT, flush 显式写回) ──

    /**
     * 管线临时数据 — 存放执行进度 (计时器/槽位/步骤).
     * 键名 "lma_pl_&lt;taskType&gt;" (<b>内存态</b>, 经 {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData}).
     * 首次调用从 NBT 加载, 之后 tick 内零 NBT 读写; 修改后需 {@link #flushPipelineData} 才落盘
     * (心跳 20t / 实体离开 / 终结自动触发). 终结时 {@link #clearPipelineData} 清缓存+NBT.
     */
    default CompoundTag pipelineData(EntityMaid maid) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, taskType());
    }

    /** 显式写回 PL 内存态到 NBT (心跳 20t / 实体离开自动调用) */
    default void flushPipelineData(EntityMaid maid) {
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.flushPl(maid, taskType());
    }

    default void clearPipelineData(EntityMaid maid) {
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.removePl(maid, taskType());
    }

    /**
     * 管线持久配置 — 跨任务保留 (材料锁定/配方缓存).
     * 键名 "lma_cfg_&lt;taskType&gt;", 不会被 onCleanup() 清除 (直读 NBT, 低频访问).
     */
    default CompoundTag pipelineConfig(EntityMaid maid) {
        // v79.63 修**配置改了不生效** (用户实机: 敲钟频率改了仍 1.5s 一敲):
        //   原用 MaidData.cfg(...) = 只读版, 对**不存在的 key 返回临时空 tag (非引用)** ⇒
        //   默认 handleConfigAction 的 putInt 写进临时 tag **不落盘** ⇒ 首次改配置必丢 ⇒
        //   管线读不到 ⇒ 退回全局默认 (钟=30t)。MaidData.cfgOrCreate 的 javadoc 早已写明这个坑
        //   (2026-08-16 修过绑定路径), 但**读写统一入口这里漏了**。
        //   ⇒ 一律走 cfgOrCreate: 读也安全 (最多多一个空标签), 写保证落盘。
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.cfgOrCreate(maid, taskType());
    }

    default void clearPipelineConfig(EntityMaid maid) {
        com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.removeCfg(maid, taskType());
    }

    // ── 行为配置 (DefaultBehaviorBrain 自动注入) ──

    /** 工作时自动吃食物 */
    default boolean enableWorkEat() { return false; }

    /** 附近容器收集过滤: null=关闭, Predicate=开启 */
    @Nullable
    default java.util.function.Predicate<net.minecraft.world.item.ItemStack> collectFilter(EntityMaid maid) { return null; }

    // ── 开关归属 (v79.63 统一 — 「运行中关开关 → 清理」与「提交门」必须同源) ──

    /**
     * 本任务的启用开关归属 (默认 {@link SwitchScope#GLOBAL})。
     *
     * <p>被动任务的启用判定有两个来源, 曾经由 {@code TaskDispatcher.submitPassive} 里一处
     * 硬编码 {@code if ("haqi"||"jiuhu_milk")} 决定 —— 而引擎侧的「运行中关开关 → cancelPassive」
     * 与逐 tick 运行判定查的是**另一套** (全局 {@code TaskToggle}), 造成关开关不清理 / 排查
     * 要同时看两个开关。现改为**任务自身声明**, 三处判定 (提交 / 运行 / 清理) 统一走
     * {@code PassiveConfigUtil.isPassiveEnabled}。
     *
     * <p>覆写返回 {@link SwitchScope#PER_MAID} 的任务: 开关存 {@code pipelineConfig(maid)}
     * 的 {@code "enabled"} 键 (子任务界面 {@code PassiveToggleConfigScreen} 编辑),
     * **缺省 false = 默认关闭**; 不覆写的任务走全局 {@code TaskToggle} (黑名单语义, 缺省开启)。
     */
    default SwitchScope switchScope() {
        return SwitchScope.GLOBAL;
    }

    /** 开关归属 — 见 {@link #switchScope()} */
    enum SwitchScope {
        /** 全局开关 ({@code config/littlemaidmoreaction/task_toggles.json}, TaskToggle, 缺省开启) */
        GLOBAL,
        /** 每女仆开关 ({@code lma_cfg_<type>.enabled}, 缺省关闭) */
        PER_MAID
    }
}
