package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 女仆持久数据统一门面 (v79.29 Phase 1) — 联合管理散落的 NBT 键体系。
 *
 * <p>分区:
 * <ul>
 *   <li>{@link #FLOW} — lma_flow_* 任务状态 (FlowTaskData 门面读)</li>
 *   <li>{@link #META} — lma_task_* / lma_saved_* 任务元数据 (TaskMetaData 门面读)</li>
 *   <li>{@link #PL} — lma_pl_&lt;type&gt; 管线临时数据 — <b>内存态</b> (tick 零 NBT, flush 显式写回)</li>
 *   <li>{@link #CFG} — lma_cfg_&lt;type&gt; 管线持久配置 — 直读 (低频访问)</li>
 *   <li>{@link #ANIM} — lma_anim_* 动画运行时键</li>
 *   <li>{@link #MISC} — 散键收编 (lma_ai_control / lma_chain_* / 节流键等)</li>
 * </ul>
 *
 * <p>线程: 服务端主线程单线程访问 (v79.27 纪律, 无锁)。
 *
 * <p>PL 内存态语义: 首次 {@link #pl} 从 NBT 加载, 之后 tick 内零 NBT 读写;
 * flush 时机 = 主动任务心跳 20t ({@link com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.GameTickPipelineManager})
 * + 实体离开世界 ({@link com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.EntityCleanupListener} flushAllPl)
 * + 任务终结 (clearPipelineData remove)。崩溃最多丢 20t 进度。
 */
public final class MaidData {

    // ── 分区 ──

    public static final int FLOW = 0;   // lma_flow_*
    public static final int META = 1;   // lma_task_* / lma_saved_*
    public static final int PL = 2;     // lma_pl_<type> — 内存态
    public static final int CFG = 3;    // lma_cfg_<type> — 直读
    public static final int ANIM = 4;   // lma_anim_* 运行时键
    public static final int MISC = 5;   // 散键收编

    private MaidData() {}

    /** PL 内存缓存 — maid 弱引用 (实体 GC 自动清, 纪律: 不用 maidId 防泄漏+ID 串扰);
     *  内层: taskType → CompoundTag (lma_pl_<type> 独立键) */
    private static final Map<EntityMaid, Map<String, CompoundTag>> PL_CACHE = new WeakHashMap<>();

    // ── PL 分区 (内存态) ──

    /** 管线临时数据 — 首次从 NBT 加载, 之后内存态 (tick 零 NBT); 修改后需 flush 才落盘 */
    public static CompoundTag pl(EntityMaid maid, String taskType) {
        Map<String, CompoundTag> byType = PL_CACHE.computeIfAbsent(maid, k -> new HashMap<>());
        return byType.computeIfAbsent(taskType,
                t -> root(maid).getCompound(TaskKeys.PL_PREFIX + t));
    }

    /** 显式写回 PL 内存态到 NBT (心跳 20t / 实体离开 / 终结调用) — 空 tag 不落盘 (remove 防旧残留) */
    public static void flushPl(EntityMaid maid, String taskType) {
        Map<String, CompoundTag> byType = PL_CACHE.get(maid);
        if (byType != null) {
            CompoundTag d = byType.get(taskType);
            if (d != null) writePl(root(maid), taskType, d);
        }
    }

    /** 实体离开: 全部 PL 键写回 + 清缓存 (跨维度传送 = leave+join, join 后重新加载) */
    public static void flushAllPl(EntityMaid maid) {
        Map<String, CompoundTag> byType = PL_CACHE.remove(maid);
        if (byType != null) {
            CompoundTag r = root(maid);
            byType.forEach((t, d) -> writePl(r, t, d));
        }
    }

    /** 空 tag → remove (pl() 首调缓存空 tag, 无条件 put 会跨 session 累积空键); 非空 → put */
    private static void writePl(CompoundTag r, String taskType, CompoundTag d) {
        if (d.isEmpty()) {
            r.remove(TaskKeys.PL_PREFIX + taskType);
        } else {
            r.put(TaskKeys.PL_PREFIX + taskType, d);
        }
    }

    /** 终结: 清 PL 缓存 + NBT 键 */
    public static void removePl(EntityMaid maid, String taskType) {
        Map<String, CompoundTag> byType = PL_CACHE.get(maid);
        if (byType != null) {
            byType.remove(taskType);
            if (byType.isEmpty()) PL_CACHE.remove(maid);
        }
        root(maid).remove(TaskKeys.PL_PREFIX + taskType);
    }

    // ── CFG 分区 (直读, 低频) ──

    public static CompoundTag cfg(EntityMaid maid, String taskType) {
        return root(maid).getCompound(TaskKeys.CFG_PREFIX + taskType);
    }

    public static void removeCfg(EntityMaid maid, String taskType) {
        root(maid).remove(TaskKeys.CFG_PREFIX + taskType);
    }

    // ── 类型化读写 (FLOW/META/ANIM/MISC 分区, root 直读写) ──

    /** 读 — 按 {@link DataKey#type()} 分发; 缺键返回 {@link DataKey#def()} (兑现 def 契约) */
    @SuppressWarnings("unchecked")  // 按 DataType 分发强制转换, 键-类型由 DataKey 泛型保证
    public static <T> T get(EntityMaid maid, DataKey<T> k) {
        CompoundTag r = root(maid);
        if (!r.contains(k.key())) return defaultValue(k);
        return switch (k.type()) {
            case STRING -> (T) r.getString(k.key());
            case INT -> (T) Integer.valueOf(r.getInt(k.key()));
            case LONG -> (T) Long.valueOf(r.getLong(k.key()));
            case BOOLEAN -> (T) Boolean.valueOf(r.getBoolean(k.key()));
            case COMPOUND -> (T) r.getCompound(k.key());
        };
    }

    /** 缺键默认值 — DataKey.def 单一真相 (COMPOUND 返回副本, 防共享默认实例被调用方改脏) */
    @SuppressWarnings("unchecked")
    public static <T> T defaultValue(DataKey<T> k) {
        if (k.type() == DataKey.DataType.COMPOUND) {
            return (T) ((net.minecraft.nbt.CompoundTag) k.def()).copy();
        }
        return k.def();
    }

    /** 写 — 按 {@link DataKey#type()} 分发 (root 直写, 语义同原 getPersistentData().putX) */
    @SuppressWarnings("unchecked")  // 同 get — 键-类型一致由 DataKey 泛型保证
    public static <T> void put(EntityMaid maid, DataKey<T> k, T v) {
        CompoundTag r = root(maid);
        switch (k.type()) {
            case STRING -> r.putString(k.key(), (String) v);
            case INT -> r.putInt(k.key(), (Integer) v);
            case LONG -> r.putLong(k.key(), (Long) v);
            case BOOLEAN -> r.putBoolean(k.key(), (Boolean) v);
            case COMPOUND -> r.put(k.key(), (CompoundTag) v);
        }
    }

    /** 存在性 — 键是否存在 (区别于 def: 写过的键才算存在) */
    public static boolean has(EntityMaid maid, DataKey<?> k) {
        return root(maid).contains(k.key());
    }

    /** 删除键 (root) */
    public static void remove(EntityMaid maid, DataKey<?> k) {
        root(maid).remove(k.key());
    }

    // ── 根访问 ──

    public static CompoundTag root(EntityMaid maid) {
        return maid.getPersistentData();
    }
}
