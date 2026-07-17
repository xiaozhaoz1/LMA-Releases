package littlemaidmoreaction.littlemaidmoreaction.api.output;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

/**
 * 物品交付提供者 SPI — 外部模组实现此接口扩展物品交付方式。
 * <p>内置实现: ItemSpawner(spawn ItemEntity + pickup delay 0)。
 */
public interface ItemOutputProvider {
    /** 唯一标识 (调试/AI上下文) */
    String id();
    /**
     * 将物品交付给女仆。返回未交付的数量(0=全部交付成功)。
     * <p>实现应尽力交付; 如果无法全部交付, 返回剩余数量让下一个Provider尝试。
     */
    int deliver(EntityMaid maid, ItemStack stack);
    /** 优先级: 较大的优先参与尝试 */
    default int priority() { return 0; }
}
