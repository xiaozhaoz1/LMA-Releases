package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 工具状态读取 — 原子只读 IO，零判断逻辑 (v36)。
 *
 * <p>高层判断（如"镐能否挖此矿"）放在计算层
 * {@link littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.ToolJudge}，
 * 本类只提供原子事实。耐久/附魔基础读取见
 * {@link littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.item.ItemOutput}。
 */
public final class ToolStateReader {

    private ToolStateReader() {}

    /** 工具材质等级 (WOOD=0, STONE=1, IRON=2, DIAMOND=3, NETHERITE=4)；非 TieredItem 返回 -1 */
    public static int getTierLevel(ItemStack stack) {
        return stack.getItem() instanceof TieredItem tiered ? tiered.getTier().getLevel() : -1;
    }

    /** 是否为镐 */
    public static boolean isPickaxe(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem;
    }

    /** 是否为斧 */
    public static boolean isAxe(ItemStack stack) {
        return stack.getItem() instanceof AxeItem;
    }

    /** MC 原生判定: 该工具破坏此方块能否产生掉落（含挖掘等级门槛，如木镐挖不动钻石矿） */
    public static boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return stack.isCorrectToolForDrops(state);
    }

    /** 剩余耐久；不可损坏物品返回 Integer.MAX_VALUE */
    public static int getRemainingDurability(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return Integer.MAX_VALUE;
        }
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    /** 物品 NBT（可 null） */
    @Nullable
    public static CompoundTag getNbt(ItemStack stack) {
        return stack.getTag();
    }
}
