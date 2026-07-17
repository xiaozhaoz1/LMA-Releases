package littlemaidmoreaction.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.input.InventoryReaderProvider;
import littlemaidmoreaction.littlemaidmoreaction.api.input.InventorySpaceProvider;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.container.WirelessChestReader;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.container.WirelessChestSpace;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid.MaidInventoryReader;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid.MaidInventorySpace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Vanilla Input 注册中心 — 聚合所有 InventorySpaceProvider + InventoryReaderProvider。
 * <p>对标 {@code BrewingRecipeRegistry} 的 Registry 模式。
 * <p>内置 Provider 在 static 块中自动注册; 外部模组通过 {@link #registerSpace}/{@link #registerReader} 扩展。
 */
public final class VanillaInputRegistry {
    private static final List<InventorySpaceProvider> SPACE = new ArrayList<>();
    private static final List<InventoryReaderProvider> READERS = new ArrayList<>();

    static {
        // 内置 Provider — 优先级: 背包=100(先查), 隙间=50(回退)
        registerSpace(new InventorySpaceProvider() {
            @Override public String id() { return "maid_inventory"; }
            @Override public int priority() { return 100; }
            @Override public int calculateSpace(EntityMaid maid, ItemStack sample) {
                return MaidInventorySpace.ofAll(maid, sample);
            }
        });
        registerSpace(new InventorySpaceProvider() {
            @Override public String id() { return "wireless_chest"; }
            @Override public int priority() { return 50; }
            @Override public int calculateSpace(EntityMaid maid, ItemStack sample) {
                return WirelessChestSpace.calculate(maid, sample);
            }
        });
        registerReader(new InventoryReaderProvider() {
            @Override public String id() { return "maid_inventory"; }
            @Override public int priority() { return 100; }
            @Override public Map<Item, Integer> readAll(EntityMaid maid) {
                return MaidInventoryReader.readAll(maid);
            }
        });
        registerReader(new InventoryReaderProvider() {
            @Override public String id() { return "wireless_chest"; }
            @Override public int priority() { return 50; }
            @Override public Map<Item, Integer> readAll(EntityMaid maid) {
                return WirelessChestReader.readAll(maid);
            }
        });
    }

    private VanillaInputRegistry() {}

    // ── 注册 ──

    public static void registerSpace(InventorySpaceProvider p) { SPACE.add(p); }
    public static void registerReader(InventoryReaderProvider p) { READERS.add(p); }

    // ── 聚合查询 ──

    /** 聚合所有 space provider 的总空间 */
    public static int totalSpace(EntityMaid maid, ItemStack sample) {
        int total = 0;
        for (var p : SPACE) total += p.calculateSpace(maid, sample);
        return total;
    }

    /** 聚合所有 reader provider 的物品列表 */
    public static Map<Item, Integer> readAllItems(EntityMaid maid) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        for (var p : READERS) {
            var items = p.readAll(maid);
            if (items != null) items.forEach((k, v) -> result.merge(k, v, Integer::sum));
        }
        return result;
    }

    /** 已注册的 space provider 数量 (调试用) */
    public static int spaceProviderCount() { return SPACE.size(); }
    /** 已注册的 reader provider 数量 (调试用) */
    public static int readerProviderCount() { return READERS.size(); }
}
