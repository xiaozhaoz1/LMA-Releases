package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** ItemStack 输出原语 */
public final class ItemOutput {
    private ItemOutput() {}

    public static void repair(ItemStack stack, int amount) {
        if (!stack.isEmpty() && stack.isDamaged())
            stack.setDamageValue(Math.max(0, stack.getDamageValue() - amount));
    }
    public static void shrink(ItemStack stack, int amount) { stack.shrink(amount); }
    public static void grow(ItemStack stack, int amount) { stack.grow(amount); }
    public static ItemStack split(ItemStack stack, int amount) { return stack.split(amount); }
    public static void setCount(ItemStack stack, int count) { stack.setCount(count); }
    public static boolean isEmpty(ItemStack stack) { return stack.isEmpty(); }
    public static boolean isDamaged(ItemStack stack) { return stack.isDamaged(); }
    public static int getMaxDamage(ItemStack stack) { return stack.getMaxDamage(); }
    public static int getDamageValue(ItemStack stack) { return stack.getDamageValue(); }
    /** 是否有任意附魔 */
    public static boolean isEnchanted(ItemStack stack) { return stack.isEnchanted(); }
    /** 获取附魔列表 (ID→等级) */
    public static java.util.Map<net.minecraft.resources.ResourceLocation, Integer> getEnchantments(ItemStack stack) {
        var map = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, Integer>();
        var enchants = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack);
        for (var entry : enchants.entrySet()) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey());
            if (key != null) map.put(key, entry.getValue());
        }
        return java.util.Collections.unmodifiableMap(map);
    }
    /** 检查是否有指定附魔 */
    public static boolean hasEnchantment(ItemStack stack, String enchantmentId) {
        if (!stack.isEnchanted()) return false;
        var rl = net.minecraft.resources.ResourceLocation.tryParse(enchantmentId);
        if (rl == null) return false;
        var ench = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(rl);
        return ench != null && stack.getEnchantmentLevel(ench) > 0;
    }
    /** 给玩家物品（背包满则掉落地上） */
    public static boolean giveToPlayer(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!player.addItem(stack)) { player.drop(stack, false); return false; }
        return true;
    }

    /** 提取女仆经验生成经验瓶 */
    public static void extractXpAsBottles(EntityMaid maid, int ratio, int maxBottles) {
        int xp = maid.getExperience();
        if (xp <= 0) return;
        int count = Math.min(xp / Math.max(1, ratio), Math.max(1, maxBottles));
        if (count <= 0) return;
        maid.setExperience(xp - count * ratio);
        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, 64);
            maid.spawnAtLocation(new ItemStack(Items.EXPERIENCE_BOTTLE, size));
            remaining -= size;
        }
    }
}
