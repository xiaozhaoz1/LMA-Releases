package littlemaidmoreaction.littlemaidmoreaction.api;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 武器→动画映射 — 根据女仆手持武器选择对应的处决动画。
 *
 * <p>从 MoreActionAPI 拆分 (v7)。</p>
 */
public final class WeaponAnimationMapper {
    static final Map<String, List<String>> WEAPON_MAP = new LinkedHashMap<>();
    private static final Random RAND = new Random();

    public static String getRandomAnimationForWeapon(Item item) {
        String path = ForgeRegistries.ITEMS.getKey(item).getPath().toLowerCase(Locale.ROOT);
        for (var e : WEAPON_MAP.entrySet()) {
            if (path.contains(e.getKey().toLowerCase(Locale.ROOT)) && !e.getValue().isEmpty()) {
                return pickRandom(e.getValue(), e.getValue().get(0));
            }
        }
        List<String> defaults = WEAPON_MAP.getOrDefault("default", List.of("execution"));
        return pickRandom(defaults, "execution");
    }

    static String pickRandom(List<String> candidates, String fallback) {
        if (candidates.isEmpty()) return fallback;
        return candidates.get(RAND.nextInt(candidates.size()));
    }

    public static Map<String, List<String>> getWeaponMappings() {
        return Collections.unmodifiableMap(WEAPON_MAP);
    }
}
