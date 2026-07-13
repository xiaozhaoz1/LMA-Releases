package littlemaidmoreaction.littlemaidmoreaction.compat;

import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * TLM 版本检测工具 — 支持多版本门控。
 *
 * <p>TLM 版本号格式为 {@code x.y.z-forge+mc1.20.1}。
 * 比较前剥离 {@code -forge} 后缀，避免 Maven 将其视为预发布标识符。</p>
 *
 * <p>用法：
 * <pre>{@code
 * if (TlmVersion.isAtLeast("1.5.1")) { ... }  // 1.5.1, 1.5.2, 1.6.0 均通过
 * if (TlmVersion.isAtLeast("1.6.0")) { ... }  // 仅 1.6.0+ 通过
 * }</pre>
 */
public final class TlmVersion {
    private static final String MOD_ID = "touhou_little_maid";
    private static volatile String cachedNumeric;
    private static volatile boolean cached = false;

    /** 获取已加载的 TLM 纯数字版本（如 "1.5.1"），未加载返回 "0" */
    public static String get() {
        if (!cached) {
            String raw = ModList.get().getModContainerById(MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("0");
            // 剥离 -forge+mc... 后缀: "1.5.1-forge+mc1.20.1" → "1.5.1"
            int dash = raw.indexOf('-');
            cachedNumeric = dash > 0 ? raw.substring(0, dash) : raw;
            cached = true;
        }
        return cachedNumeric;
    }

    /**
     * TLM 版本是否 >= 指定版本。
     * 使用 Maven {@link ComparableVersion}，正确处理 1.5.1 / 1.5.2 / 1.6.0 等。
     */
    public static boolean isAtLeast(String minVersion) {
        String current = get();
        if ("0".equals(current)) return false;
        try {
            return new ComparableVersion(current).compareTo(
                   new ComparableVersion(minVersion)) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── 版本门控快捷方法 ──────────────────────────────────────

    /** TLM >= 1.5.1（MaidRequestItemEvent 等） */
    public static boolean isV151() { return isAtLeast("1.5.1"); }

    /** TLM >= 1.5.2（预留） */
    public static boolean isV152() { return isAtLeast("1.5.2"); }

    /** TLM >= 1.6.0（预留） */
    public static boolean isV160() { return isAtLeast("1.6.0"); }

    private TlmVersion() {}
}
