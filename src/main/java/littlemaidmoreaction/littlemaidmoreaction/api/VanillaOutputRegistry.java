package littlemaidmoreaction.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.output.ItemOutputProvider;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.item.ItemSpawner;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla Output 注册中心 — 聚合所有 ItemOutputProvider。
 * <p>对标 {@code BrewingRecipeRegistry} 的 Registry 模式。
 */
public final class VanillaOutputRegistry {
    private static final List<ItemOutputProvider> OUTPUTS = new ArrayList<>();

    static {
        register(new ItemOutputProvider() {
            @Override public String id() { return "item_spawn"; }
            @Override public int priority() { return 100; }
            @Override public int deliver(EntityMaid maid, ItemStack stack) {
                ItemSpawner.spawnForPickup(maid, stack);
                return 0; // 全部交付
            }
        });
    }

    private VanillaOutputRegistry() {}

    public static void register(ItemOutputProvider p) { OUTPUTS.add(p); }

    /** 依次尝试所有 Provider 交付物品。未交付的物品留在地面(自然掉落)。 */
    public static void deliver(EntityMaid maid, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        for (var p : OUTPUTS) {
            int remaining = p.deliver(maid, stack);
            if (remaining <= 0) return;
            // stack count shrinks for next provider
            stack = stack.copyWithCount(remaining);
        }
    }

    public static int providerCount() { return OUTPUTS.size(); }
}
