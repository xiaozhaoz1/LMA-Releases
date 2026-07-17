package littlemaidmoreaction.littlemaidmoreaction.task.service;

import net.minecraft.nbt.CompoundTag;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一多 tick 任务状态管理 — 替代 CompoundTag + ConcurrentHashMap 混用。
 *
 * <p>提供两个工厂方法:
 * <ul>
 *   <li>{@link #persistent(CompoundTag, String)} — NBT 持久化 (跨 session 存活)</li>
 *   <li>{@link #memory(int)} — 内存存储 (女仆卸载时清理)</li>
 * </ul>
 */
public interface TaskState {
    long getLong(String key, long def);
    void setLong(String key, long value);
    int getInt(String key, int def);
    void setInt(String key, int value);
    long[] getLongArray(String key);
    void setLongArray(String key, long[] values);
    void remove(String key);
    void clear();

    /** NBT 持久化实现 */
    static TaskState persistent(CompoundTag tag, String prefix) {
        return new PersistentTaskState(tag, prefix);
    }

    /** 内存实现 (女仆卸载时清理) */
    static TaskState memory(int maidId) {
        return new MemoryTaskState(maidId);
    }
}

/** NBT 持久化状态 */
final class PersistentTaskState implements TaskState {
    private final CompoundTag tag;
    private final String prefix;

    PersistentTaskState(CompoundTag tag, String prefix) {
        this.tag = tag;
        this.prefix = prefix;
    }

    private String key(String k) { return prefix + k; }

    @Override public long getLong(String k, long def) { return tag.contains(key(k)) ? tag.getLong(key(k)) : def; }
    @Override public void setLong(String k, long v) { tag.putLong(key(k), v); }
    @Override public int getInt(String k, int def) { return tag.contains(key(k)) ? tag.getInt(key(k)) : def; }
    @Override public void setInt(String k, int v) { tag.putInt(key(k), v); }
    @Override public long[] getLongArray(String k) { return tag.getLongArray(key(k)); }
    @Override public void setLongArray(String k, long[] v) { tag.putLongArray(key(k), v); }
    @Override public void remove(String k) { tag.remove(key(k)); }
    @Override public void clear() {
        var iter = tag.getAllKeys().iterator();
        while (iter.hasNext()) {
            if (iter.next().startsWith(prefix)) iter.remove();
        }
    }
}

/** 内存状态 (ConcurrentHashMap, 女仆卸载时清理) */
final class MemoryTaskState implements TaskState {
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<String, Object>> STORE = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> map;

    MemoryTaskState(int maidId) {
        this.map = STORE.computeIfAbsent(maidId, k -> new ConcurrentHashMap<>());
    }

    @Override public long getLong(String k, long def) {
        Object v = map.get(k); return v instanceof Number n ? n.longValue() : def;
    }
    @Override public void setLong(String k, long v) { map.put(k, v); }
    @Override public int getInt(String k, int def) {
        Object v = map.get(k); return v instanceof Number n ? n.intValue() : def;
    }
    @Override public void setInt(String k, int v) { map.put(k, v); }
    @Override public long[] getLongArray(String k) {
        Object v = map.get(k); return v instanceof long[] a ? a : new long[0];
    }
    @Override public void setLongArray(String k, long[] v) { map.put(k, v); }
    @Override public void remove(String k) { map.remove(k); }
    @Override public void clear() { map.clear(); STORE.remove(map.hashCode()); }
}
