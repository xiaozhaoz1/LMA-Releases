package littlemaidmoreaction.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.ItemResolver;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.container.ContainerOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * 容器物品存取 — 委托 PutInContainerAction + TakeFromContainerAction。
 * <p>使用 BlockSearch 查找 IItemHandler + ItemResolver 解析物品 + ContainerOutput 存取。
 */
public final class ContainerExecute {
    private ContainerExecute() {}

    /** 女仆→容器: 将指定物品存入附近容器 */
    public static boolean deposit(ServerLevel world, EntityMaid maid, String itemId, int count, int range) {
        var item = ItemResolver.resolve(itemId);
        if (item == null) return false;
        var results = BlockSearch.findBlocks(world, maid.blockPosition(), range, 4,
            (pos, state) -> {
                BlockEntity te = world.getBlockEntity(pos);
                return te != null && te.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
            });
        if (results.isEmpty()) return false;
        for (var match : results) {
            var te = world.getBlockEntity(match.pos());
            if (te == null) continue;
            var handler = te.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler != null && ContainerOutput.depositItem(maid, handler, item, count))
                return true;
        }
        return false;
    }

    /** 容器→女仆: 从附近容器提取指定物品 */
    public static boolean withdraw(ServerLevel world, EntityMaid maid, String itemId, int count, int range) {
        var item = ItemResolver.resolve(itemId);
        if (item == null) return false;
        var results = BlockSearch.findBlocks(world, maid.blockPosition(), range, 4,
            (pos, state) -> {
                BlockEntity te = world.getBlockEntity(pos);
                return te != null && te.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
            });
        if (results.isEmpty()) return false;
        for (var match : results) {
            var te = world.getBlockEntity(match.pos());
            if (te == null) continue;
            var handler = te.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler != null && ContainerOutput.withdrawItem(maid, handler, item, count))
                return true;
        }
        return false;
    }
}
