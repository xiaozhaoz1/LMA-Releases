package com.github.xiaozhaoz1.littlemaidmoreaction.compat;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 兼容模块开关 (v77) — per-module 启停, 注册期生效 (重启应用)。
 *
 * <p>JSON: config/littlemaidmoreaction/compat_toggles.json
 * <pre>{"disabled":["create","numen"]}</pre>
 * 缺失文件 = 全启用 (向后兼容); 未知 id 惰性存储 (无人查询, 前向兼容)。
 *
 * <p>★ 与 TaskToggle 的两处刻意偏离 (纯 JVM 可测性, 勿"简化"回去):
 * <ul>
 *   <li>文件路径是方法 {@code file()} 而非 static 字段 — static 字段在 <clinit> 抛
 *       ExceptionInInitializerError 会永久毒化类 (FMLPaths.CONFIGDIR 纯 JVM 不可用)</li>
 *   <li>{@code load()} 双 catch: IOException 记 warn; Throwable 静默 —
 *       纯 JVM 测试类加载触发 {@link LittleMaidMoreAction} clinit 失败时降级全启用</li>
 * </ul>
 *
 * <p>时序 (构造期): {@link CompatRegistry#scanAllCompatEarly} → doScan → {@link #load()}
 * 先于 TaskRegistry static 块 (TLM 扩展实例化在 FMLCommonSetupEvent, 晚于所有 mod 构造)。
 */
public final class CompatToggle {

    private static final String FILE_NAME = "compat_toggles.json";
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();

    static { load(); }

    // ── enabled ──
    public static boolean isModuleEnabled(String moduleId) { return !DISABLED.contains(moduleId); }

    public static void setModuleEnabled(String moduleId, boolean v) {
        if (v) DISABLED.remove(moduleId); else DISABLED.add(moduleId);
        save();
    }

    public static Set<String> disabledModules() { return Collections.unmodifiableSet(DISABLED); }

    // ── JSON ──
    /** 显式提前加载 (CompatRegistry.doScan 构造期调用) — 幂等: 清空后重读 (replace 语义) */
    static void load() {
        try {
            loadFrom(file());
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.warn("[CompatToggle] load failed", e);
        } catch (Throwable t) {
            // 纯 JVM 测试: FML 未初始化 (CONFIG_DIR 不可用) — 保持全启用默认
        }
    }

    private static void save() {
        try {
            saveTo(file());
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.warn("[CompatToggle] save failed", e);
        } catch (Throwable t) {
            // 同 load
        }
    }

    private static Path file() {
        return LittleMaidMoreAction.CONFIG_DIR.resolve(FILE_NAME);
    }

    // ── 测试钩子 (package-private, 纯 Path 操作, 零 FML 依赖) ──

    static void loadFrom(Path file) throws IOException {
        DISABLED.clear();
        if (!Files.exists(file)) return;   // 缺失 = 全启用 (向后兼容)
        String json = Files.readString(file);
        int i = json.indexOf("\"disabled\"");
        if (i < 0) return;
        int s = json.indexOf('[', i), e = json.indexOf(']', s);
        if (s < 0 || e < 0) return;
        for (String t : json.substring(s + 1, e).split(",")) {
            String v = t.trim().replace("\"", "");
            if (!v.isEmpty()) DISABLED.add(v);
        }
    }

    static void saveTo(Path file) throws IOException {
        Files.writeString(file, serialize(DISABLED));
    }

    static String serialize(Set<String> disabled) {
        StringBuilder sb = new StringBuilder("{\"disabled\":[");
        var it = disabled.iterator();
        while (it.hasNext()) {
            sb.append('"').append(it.next()).append('"');
            if (it.hasNext()) sb.append(',');
        }
        return sb.append("]}").toString();
    }

    private CompatToggle() {}
}
