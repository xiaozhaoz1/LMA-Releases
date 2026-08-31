package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid;

import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MaidInventorySpace#calculate} 空间计算测试 (纯 JVM 安全子集)。
 *
 * <p><b>可测性边界</b> (2026-08-11b 实测, 错题 #174): mock/实例化 ItemStack 触发其
 * 类初始化 → {@code FeatureElement clinit → Registries.clinit → BuiltInRegistries 初始化
 * 失败} (纯 JVM 无注册表数据; mockStatic(ItemStackHelper) 亦先经 mock(ItemStack) 炸)。
 * 因此真实空间计算用例 (需 ItemStack 实例) 在此铁律下不可行 — 测试仅覆盖 null/空守卫:
 * <ul>
 *   <li>null 物品栏 → 0</li>
 *   <li>null/空 sample → 0</li>
 * </ul>
 * 真实计算路径 (空槽/同物品叠加/满槽钳制) 依赖 ItemStack 实例, 覆盖手段 = gametest
 * (有 MC 环境) — 见 COMMON.md §11 测试专项注记。
 */
class MaidInventorySpaceTest {

    /** 匿名 IItemHandler — 空槽实现 (calculate 仅需 getSlots/getStackInSlot) */
    private static IItemHandler invOf(int slots) {
        return new IItemHandler() {
            @Override public int getSlots() { return slots; }
            @Override public net.minecraft.world.item.ItemStack getStackInSlot(int s) { return net.minecraft.world.item.ItemStack.EMPTY; }
            @Override public net.minecraft.world.item.ItemStack insertItem(int s, net.minecraft.world.item.ItemStack stack, boolean sim) { return stack; }
            @Override public net.minecraft.world.item.ItemStack extractItem(int s, int amount, boolean sim) { return null; }
            @Override public int getSlotLimit(int s) { return 64; }
            @Override public boolean isItemValid(int s, net.minecraft.world.item.ItemStack stack) { return true; }
        };
    }

    // ─── null/空守卫 (纯 JVM 可达) ────────────────────────────────

    @Test
    @DisplayName("null 物品栏 → 0")
    void calculate_nullInv_returnsZero() {
        assertEquals(0, MaidInventorySpace.calculate(null, null));
    }

    @Test
    @DisplayName("null sample → 0")
    void calculate_nullSample_returnsZero() {
        assertEquals(0, MaidInventorySpace.calculate(invOf(2), null));
    }
}
