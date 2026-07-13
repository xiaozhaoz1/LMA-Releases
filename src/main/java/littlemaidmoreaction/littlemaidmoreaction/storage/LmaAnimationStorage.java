package littlemaidmoreaction.littlemaidmoreaction.storage;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.model.LmaAnimationDef;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.*;
import java.util.Optional;

/**
 * 动画元数据持久化 — config/littlemaidmoreaction/animationsetup/<name>.json
 *
 * <p>每文件存储一个 LmaAnimationDef 的 JSON。
 * 文件名 = 动画名 + ".json"。
 * 动画名可能包含路径分隔符（如 "combat/slash"），转换为文件路径时替换为下划线。
 */
public final class LmaAnimationStorage {
    private static final Path DIR =
            LittleMaidMoreAction.CONFIG_DIR.resolve("animationsetup");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 将动画名转为安全文件名 */
    private static Path filePath(String animName) {
        String safe = animName.replace('/', '_').replace('\\', '_');
        return DIR.resolve(safe + ".json");
    }

    /** 检查动画配置文件是否存在 */
    public static boolean exists(String animName) {
        return Files.exists(filePath(animName));
    }

    /** 读取动画元数据 */
    public static Optional<LmaAnimationDef> get(String animName) {
        Path file = filePath(animName);
        if (!Files.exists(file)) return Optional.empty();
        try {
            String json = Files.readString(file);
            return Optional.of(GSON.fromJson(json, LmaAnimationDef.class));
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn("[AnimStorage] 读取失败: {} — {}", animName, e.getMessage());
            return Optional.empty();
        }
    }

    /** 写入/更新动画元数据（自动创建目录） */
    public static void put(LmaAnimationDef def) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(filePath(def.name()), GSON.toJson(def));
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.error("[AnimStorage] 保存失败: {} — {}", def.name(), e.getMessage());
        }
    }

    /** 删除动画元数据 */
    public static void remove(String animName) {
        try { Files.deleteIfExists(filePath(animName)); }
        catch (Exception e) {
            LittleMaidMoreAction.LOGGER.error("[AnimStorage] 删除失败: {}", animName);
        }
    }
}
