package littlemaidmoreaction.littlemaidmoreaction.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/** 物品 ID 解析 — 消除 21 处 ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id)) 重复 */
public final class ItemResolver {
    private ItemResolver() {}

    /** 按 ID 字符串解析物品，失败返回 null */
    @Nullable
    public static Item resolve(String itemId) {
        var rl = ResourceLocation.tryParse(itemId);
        return rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
    }
    /** 检查物品 ID 是否有效 */
    public static boolean exists(String itemId) { return resolve(itemId) != null; }
}
