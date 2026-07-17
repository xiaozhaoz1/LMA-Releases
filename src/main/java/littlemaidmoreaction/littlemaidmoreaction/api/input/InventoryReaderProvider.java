package littlemaidmoreaction.littlemaidmoreaction.api.input;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * 物品读取提供者 SPI — 外部模组实现此接口扩展物品来源。
 * <p>内置实现: MaidInventoryReader(背包), WirelessChestReader(隙间箱子)。
 */
public interface InventoryReaderProvider {
    /** 唯一标识 (调试/AI上下文) */
    String id();
    /** 读取此来源的全部物品。返回空Map或null=无物品。 */
    Map<Item, Integer> readAll(EntityMaid maid);
    /** 优先级: 较大的优先参与提取排序 */
    default int priority() { return 0; }
}
