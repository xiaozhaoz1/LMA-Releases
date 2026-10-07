package com.github.xiaozhaoz1.littlemaidmoreaction.api.input;

/**
 * 木棍标记/绑定 + 任务类型 的**最小能力供应 SPI** (2026-09-21 接口倒置)。
 *
 * <p><b>为什么需要它</b>: {@code StickBindUtil} 下沉 {@code vanilla/input/world/} 后, 既不能
 * import {@code adapter} (原用 {@code LmaTaskTypeRegistry.extractTaskType}) 也不能
 * import {@code config} (原用 {@code ActiveTaskConfig.BI_MARK_ITEM/BI_BIND_ITEM}) ——
 * 两者都在 {@code ArchGuardTest} 的 vanilla 白名单之外 (白名单实测 = {@code api.VanillaConstants}
 * + {@code vanilla.*} + 根包类)。⇒ 用「api 侧接口 + 启动期注入」把这两处依赖倒置出去。
 *
 * <p><b>为什么是 3 个方法</b>: 三个都是消费点的**真实最小需求** —— 两个配置值 (标记物/绑定物 id)
 * + 一个纯函数 (uid path → task_type); 少一个都会让搬迁后的 vanilla 类重新出现禁止依赖 ✗。
 *
 * <p><b>实现方</b>: {@code init.LmaTaskBinding} (读 config + 委托 {@code task/api/TaskTypeUid};
 * 纯值 + 纯函数, **无状态/无注册表**) ⇒ 注入时序无约束。
 * <b>注入</b>: {@code init.LmaRegistrar.init()} (双平台入口都调; 幂等) 见 {@link StickBindAccess#install}。
 * <b>消费方</b>: {@code vanilla/input/world/StickBindUtil} 经 {@link StickBindAccess} 访问。
 */
public interface StickBindProvider {

    /** 标记物品 id 原始串 (config `active.block_interact.mark_item`; 空/非法 ⇒ 消费方自行回退木棍) */
    String markItemId();

    /** 绑定物品 id 原始串 (config `active.block_interact.bind_item`; 空/非法 ⇒ 消费方自行回退木棍) */
    String bindItemId();

    /** 任务类型: uid path → task_type (如 `task/craft_chain` → `craft_chain`; 纯函数) */
    String taskTypeOf(String uidPath);
}
