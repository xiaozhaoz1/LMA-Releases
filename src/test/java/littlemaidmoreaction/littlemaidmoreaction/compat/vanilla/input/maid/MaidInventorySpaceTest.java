package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid;

import net.minecraftforge.items.IItemHandler;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 空间计算测试 — null/边界守卫。
 * <p>算法验收需游戏环境: new ItemStackHandler(3), slot0=STICK×32 → 32+64+64=160。
 * <p>纯JUnit5无法加载 Minecraft Registry, ItemStack 不可用。
 */
public class MaidInventorySpaceTest {

    @Test
    void calculate_nullInv_returnsZero() {
        assertEquals(0, MaidInventorySpace.calculate(null, null));
    }

    @Test
    void calculate_nullSample_returnsZero() {
        IItemHandler inv = new IItemHandler() {
            @Override public int getSlots() { return 3; }
            @Override public ItemStack getStackInSlot(int s) { return null; }
            @Override public ItemStack insertItem(int s, ItemStack stack, boolean sim) { return stack; }
            @Override public ItemStack extractItem(int s, int amount, boolean sim) { return null; }
            @Override public int getSlotLimit(int s) { return 64; }
            @Override public boolean isItemValid(int s, ItemStack stack) { return true; }
        };
        assertEquals(0, MaidInventorySpace.calculate(inv, null));
    }
}
