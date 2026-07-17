package littlemaidmoreaction.littlemaidmoreaction.api.maideditor;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 女仆编辑器字段注册中心。替代硬编码 GROUPS + readField() switch。
 *
 * <h3>外部模组用法</h3>
 * <pre>{@code
 * MaidEditorRegistry.addGroup("我的模组");
 * MaidEditorRegistry.addField("我的模组", "魔力", "mana", FieldType.INT, "maxMana", "/",
 *     m -> String.valueOf(MyMod.getMana(m)),
 *     (m, v) -> MyMod.setMana(m, Integer.parseInt(v)));
 * }</pre>
 */
public final class MaidEditorRegistry {
    private static final Map<String, List<EditorField>> GROUPS = new LinkedHashMap<>();
    private static final Set<String> KEYS = new HashSet<>();

    private MaidEditorRegistry() {}

    /** 添加分组（重复调用不覆盖） */
    public static synchronized void addGroup(String name) {
        GROUPS.putIfAbsent(name, new ArrayList<>());
    }

    /** 添加字段；组不存在则自动创建；key 冲突则跳过 */
    public static synchronized void addField(String group, String label, String key,
                                              FieldType type, @Nullable String secKey, @Nullable String secPrefix,
                                              Function<EntityMaid, String> reader,
                                              BiConsumer<EntityMaid, String> writer) {
        if (!KEYS.add(key)) return; // key 去重
        GROUPS.computeIfAbsent(group, k -> new ArrayList<>());
        GROUPS.get(group).add(new EditorField(label, key, type, secKey, secPrefix, reader, writer));
    }

    public static List<String> getGroups() { return List.copyOf(GROUPS.keySet()); }

    public static List<EditorField> getFields(String group) {
        var list = GROUPS.get(group);
        return list != null ? List.copyOf(list) : List.of();
    }

    public static int groupCount() { return GROUPS.size(); }

    /** 字段条目 */
    public record EditorField(String label, String key, FieldType type,
                               @Nullable String secKey, @Nullable String secPrefix,
                               Function<EntityMaid, String> reader,
                               BiConsumer<EntityMaid, String> writer) {}
}
