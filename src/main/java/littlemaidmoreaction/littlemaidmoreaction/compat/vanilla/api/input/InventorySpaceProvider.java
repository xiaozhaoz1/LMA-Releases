package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.input;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

/**
 * 存储空间提供者 SPI — 外部模组实现此接口扩展女仆物品存储容量。
 * <p>在 mod 构造器或 ILittleMaid 扩展中调用 {@code VanillaInputRegistry.registerSpace()} 注册。
 * <p>内置实现: MaidInventorySpace(背包), WirelessChestSpace(隙间箱子)。
 */
public interface InventorySpaceProvider {
    /** 唯一标识 (调试/AI上下文) */
    String id();
    /** 计算此存储能容纳多少 sample 物品。返回0=不参与。 */
    int calculateSpace(EntityMaid maid, ItemStack sample);
    /** 优先级: 较大的优先参与提取排序 */
    default int priority() { return 0; }
}
