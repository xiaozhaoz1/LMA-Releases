package littlemaidmoreaction.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.Animation;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.google.gson.Gson;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 动画时长管理器 — 时长加载、缓存、查询。
 *
 * <p>从 MoreActionAPI 拆分 (v7)，职责单一：管理动画名→时长(tick) 的映射。</p>
 */
public final class AnimationDurationManager {
    static final Map<String, Integer> DURATIONS = new LinkedHashMap<>();
    private static final int DEFAULT_ANIMATION_TICK = 40;

    /** 服务端启动时加载映射数据 — 从 config 目录读取动画 JSON 文件解析时长。 */
    public static void loadServerDurations() {
        loadDurationsFromOwnFiles();
        LittleMaidMoreAction.LOGGER.info("[LMA/Duration] 服务端动画数据已加载，共 {} 个动画", DURATIONS.size());
    }

    /** 客户端加载动画时长 — 从 GeckoLibCache + 直接解析动画 JSON */
    @OnlyIn(Dist.CLIENT)
    public static void loadClientDurations() {
        DURATIONS.clear();
        AnimationFile file = GeckoModelLoader.DEFAULT_MAID_ANIMATION_FILE;
        if (file != null) {
            for (Map.Entry<String, Animation> e : file.animations().entrySet()) {
                DURATIONS.put(e.getKey(), (int) Math.ceil(e.getValue().animationLength));
            }
        }
        for (var entry : GeckoLibCache.getInstance().getAnimations().entrySet()) {
            for (var animEntry : entry.getValue().animations().entrySet()) {
                DURATIONS.put(animEntry.getKey(), (int) Math.ceil(animEntry.getValue().animationLength));
            }
        }
        loadDurationsFromOwnFiles();
        if (MoreActionConfig.DEBUG_MODE.get()) checkFallbackAnimations();
        LittleMaidMoreAction.LOGGER.info("[LMA/Duration] 客户端动画数据已刷新，共 {} 个动画", DURATIONS.size());
    }

    /** 从 config 目录加载动画文件时长 — 服务端+客户端通用（无 {@code @OnlyIn} 限制）。 */
    private static void loadDurationsFromOwnFiles() {
        Path animDir = LittleMaidMoreAction.CONFIG_DIR.resolve("animations");
        if (!Files.isDirectory(animDir)) return;
        for (String fileName : littlemaidmoreaction.littlemaidmoreaction.storage.StartupLoader.getAnimationFiles()) {
            Path file = animDir.resolve(fileName);
            if (!Files.isRegularFile(file)) continue;
            try {
                String json = Files.readString(file);
                var root = new Gson().fromJson(json, com.google.gson.JsonObject.class);
                if (root == null || !root.has("animations")) continue;
                var anims = root.getAsJsonObject("animations");
                for (var entry : anims.entrySet()) {
                    String animName = entry.getKey();
                    if (DURATIONS.containsKey(animName)) continue;
                    try {
                        double lenSec = entry.getValue().getAsJsonObject().get("animation_length").getAsDouble();
                        DURATIONS.put(animName, (int) Math.ceil(lenSec * 20));
                    } catch (Exception ignored) {
                        DURATIONS.putIfAbsent(animName, DEFAULT_ANIMATION_TICK);
                    }
                }
            } catch (IOException e) {
                LittleMaidMoreAction.LOGGER.warn("[LMA/Duration] 读取动画文件失败: {} — {}", fileName, e.getMessage());
            }
        }
    }

    static boolean checkFallbackAnimations() {
        String[][] fallbacks = {{"execution","处决"},{"animation.flash1","闪避"},{"animation.Mock1","嘲讽"},{"parry","弹反"}};
        boolean allOk = true;
        for (String[] fb : fallbacks) {
            if (!DURATIONS.containsKey(fb[0])) {
                LittleMaidMoreAction.LOGGER.warn("[Debug] 兜底动画 '{}'（{}）不存在！", fb[0], fb[1]);
                allOk = false;
            }
        }
        if (allOk) LittleMaidMoreAction.LOGGER.info("[Debug] 兜底动画验证通过");
        return allOk;
    }

    public static int getAnimationDuration(String animName) {
        return DURATIONS.getOrDefault(animName, DEFAULT_ANIMATION_TICK);
    }

    public static boolean isAnimationValid(String animName) { return DURATIONS.containsKey(animName); }

    public static Map<String, Integer> getAllAnimationDurations() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(DURATIONS));
    }
}
