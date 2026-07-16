package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 女仆可读属性注册 — TLM 内置 + 原版，仅供 {@code maid_attr} 条件读取。
 */
public final class MaidAttrRegistry {

    public record Entry(String key, String display, String category, String valueType) {}

    private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();
    /** key → Attribute 的懒加载缓存 */
    private static final Map<String, Attribute> ATTR_CACHE = new LinkedHashMap<>();

    static {
        final String NS = "touhou_little_maid";
        // TLM
        tlm("maid_use_item_speed",          "物品使用速度",    NS, "工作", "num");
        tlm("maid_crossbow_attack_speed",   "弩攻击速度",      NS, "战斗", "num");
        tlm("maid_gun_attack_speed",        "枪械攻击速度",    NS, "战斗", "num");
        tlm("maid_shoot_cooldown",          "射击冷却",        NS, "战斗", "num");
        tlm("maid_trident_cooldown",        "三叉戟冷却",      NS, "战斗", "num");
        tlm("maid_pickup_range",            "拾取范围",        NS, "工作", "num");
        tlm("maid_passive_use_shield_tick", "盾牌时间",        NS, "生存", "num");
        tlm("maid_hunger",                  "饱食度",          NS, "生存", "num");
        // 原版
        van("max_health",       "最大生命",     Attributes.MAX_HEALTH,       "生存", "num");
        van("attack_damage",    "攻击力",       Attributes.ATTACK_DAMAGE,    "战斗", "num");
        van("attack_speed",     "攻击速度",     Attributes.ATTACK_SPEED,     "战斗", "num");
        van("armor",            "护甲值",       Attributes.ARMOR,            "生存", "num");
        van("armor_toughness",  "盔甲韧性",     Attributes.ARMOR_TOUGHNESS,  "生存", "num");
        van("luck",             "幸运",         Attributes.LUCK,             "其他", "num");
        van("movement_speed",   "移动速度",     Attributes.MOVEMENT_SPEED,   "其他", "num");
        van("knockback_resist", "击退抗性",     Attributes.KNOCKBACK_RESISTANCE, "生存", "num");
        van("follow_range",     "跟随范围",     Attributes.FOLLOW_RANGE,     "其他", "num");
    }

    private MaidAttrRegistry() {}

    private static void tlm(String id, String display, String ns, String cat, String valueType) {
        REGISTRY.put(id, new Entry(id, display, cat, valueType));
        ATTR_CACHE.put(id, null); // 占位，首次 get 时延迟解析
    }

    private static void van(String key, String display, Attribute attr, String cat, String valueType) {
        REGISTRY.put(key, new Entry(key, display, cat, valueType));
        ATTR_CACHE.put(key, attr);
    }

    public static List<Entry> getAll() { return List.copyOf(REGISTRY.values()); }

    public static Entry getDef(String key) { return REGISTRY.get(key); }

    public static double get(EntityMaid maid, String key) {
        Attribute attr = resolve(key);
        return attr != null ? maid.getAttributeValue(attr) : 0.0;
    }

    /** 修改属性基础值。等价于 {@code /attribute base set}。 */
    public static void setBase(EntityMaid maid, String key, double value) {
        Attribute attr = resolve(key);
        if (attr == null) return;
        AttributeInstance inst = maid.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static Attribute resolve(String key) {
        Attribute cached = ATTR_CACHE.get(key);
        if (cached != null) return cached;
        Entry e = REGISTRY.get(key);
        if (e == null) return null;
        // TLM 属性：从 ForgeRegistries 解析
        Attribute resolved = ForgeRegistries.ATTRIBUTES.getValue(
                ResourceLocation.fromNamespaceAndPath("touhou_little_maid", key));
        ATTR_CACHE.put(key, resolved);
        return resolved;
    }
}
