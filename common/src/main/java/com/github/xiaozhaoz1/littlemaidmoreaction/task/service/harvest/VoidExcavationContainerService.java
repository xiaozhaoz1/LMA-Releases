package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
//?}

/**
 * v79.62 挖空置域容器层 — 输入箱 (工具) / 输出箱 (方块) + 1×1 4方向搜索扩展。
 *
 * <p>标记箱 (PD 存 pos): 输入箱放工具供女仆换, 输出箱收挖出的方块.
 * <b>1×1 搜索扩展</b>: 标记箱满 (输出) 或空 (输入) → 向旁边 1 格 4 方向 (N/S/E/W)
 * 搜索其他容器, 可用则切换; 仍满/空 → 继续搜下一个方向, 直到 4 方向全搜完 → 回玩家提醒.
 * 搜索缓存存 PD (每轮查, 箱状态变化自动推进).
 */
public final class VoidExcavationContainerService {

    private VoidExcavationContainerService() {}

    /** 取容器 handler — 复用 ContainerOutput (capability 六方向) */
    public static IItemHandler getHandler(ServerLevel world, BlockPos pos) {
        return pos == null ? null : ContainerOutput.getHandler(world, pos);
    }

    /** 输入箱: 取工具到女仆背包 (v79.62.1 修: 筛选工具, 原 withdrawAny 取任意物品会取非工具).
     *  遍历箱槽找工具取整组 (进背包, ensureToolFor 按需换手). 返回是否取到工具. */
    public static boolean takeToolFromInput(ServerLevel world, EntityMaid maid, BlockPos inputPos) {
        IItemHandler handler = getHandler(world, inputPos);
        if (handler == null) return false;
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty() || !isTool(s)) continue;   // 只取工具 (damageable)
            ItemStack extracted = handler.extractItem(i, Math.min(8, s.getCount()), false);
            if (extracted.isEmpty()) continue;
            // 塞女仆背包, 溢出退还容器
            for (int j = 0; j < inv.getSlots() && !extracted.isEmpty(); j++) {
                extracted = inv.insertItem(j, extracted, false);
            }
            if (!extracted.isEmpty()) {
                // ★ v79.68.1 退还也要接余量 (错题 #354 族审计): 退还目标是**刚被 extract 的同一槽**,
                //   正常必有空间 ⇒ 不可复现; 但 handler 若有 insert 筛选语义 (mod 容器) 会拒收 ⇒ 余量落地, 绝不消失 ✓
                ItemStack back = handler.insertItem(i, extracted, false);
                if (!back.isEmpty()) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.ItemSpawner
                            .spawnForPickup(maid, back);
                }
            }
            return true;   // 取到工具
        }
        return false;
    }

    /** 输出箱: 把女仆背包的方块存入. 返回剩余未存 (0=全部存完).
     *  <p>防复制 (用户裁定): 先 extractItem 原子取走原栈 → 插容器 → 剩余插回原槽.
     *  原栈取走即扣, 绝无 copy+shrink 竞态复制 (对齐 ContainerOutput 安全模式). */
    public static int depositBlocks(ServerLevel world, EntityMaid maid, BlockPos outputPos) {
        IItemHandler handler = getHandler(world, outputPos);
        if (handler == null) return 0;
        int remaining = 0;
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || isTool(s) || blockStateOf(s) == null) continue;   // 工具/非方块(食物花等)不存输出箱
            ItemStack taken = inv.extractItem(i, s.getCount(), false);
            if (taken.isEmpty()) continue;
            int before = taken.getCount();
            // 逐槽插入 (失败剩余留在 taken)
            for (int j = 0; j < handler.getSlots() && !taken.isEmpty(); j++) {
                taken = handler.insertItem(j, taken, false);
            }
            int deposited = before - taken.getCount();
            if (!taken.isEmpty()) {
                // 容器满插不进去 → 插回女仆原槽 (剩余)
                // v79.62.1 修"东西消失": insertItem 返回值 = 插不进的 (原槽被占/堆叠满),
                // 忽略会静默丢物品 → 插不进的丢到女仆脚下 (item entity, 不消失)
                ItemStack rest = inv.insertItem(i, taken, false);
                if (!rest.isEmpty()) {
                    maid.spawnAtLocation(rest, 0.5f);
                }
                remaining += rest.getCount();
            }
            if (deposited <= 0 && taken.getCount() == before) {
                break;   // 完全插不进 → 后续槽同理, 停止
            }
        }
        return remaining;
    }

    /** 判断是否工具 (镐/锹/斧/剑 — 不存输出箱, 留背包换手) */
    private static boolean isTool(ItemStack s) {
        // 简单判断: 可损坏物品 = 工具/武器/护甲 (挖出的石头泥土无耐久)
        return s.isDamageableItem();
    }

    /** 取一个可放置方块用于垫液体 (v79.62.1 用户裁定: 空置域独有液体处理, 不刷物品).
     *  <p>优先从女仆背包原子取 1 个非工具方块 (extractItem → 即扣, 无 copy 复制);
     *  背包无 → 从输出箱原子取回 1 个 (取回即扣箱, 垫完 = 物从箱到世界, 不凭空).
     *  都无 → null (垫不了, 调用方提示需方块). */
    @javax.annotation.Nullable
    public static net.minecraft.world.level.block.state.BlockState takeBlockForFill(
            ServerLevel world, EntityMaid maid, @javax.annotation.Nullable BlockPos outputPos) {
        // 背包优先 (女仆挖出的方块)
        ItemStack fromInv = takeOnePlaceable(maid.getAvailableInv(false), maid);
        if (!fromInv.isEmpty()) {
            return blockStateOf(fromInv);
        }
        // 背包无 → 从输出箱取回 (原子: extractItem 即扣箱)
        if (outputPos != null) {
            IItemHandler handler = getHandler(world, outputPos);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack s = handler.getStackInSlot(i);
                    if (s.isEmpty() || isTool(s)) continue;
                    net.minecraft.world.level.block.state.BlockState bs = blockStateOf(s);
                    if (bs == null) continue;
                    ItemStack got = handler.extractItem(i, 1, false);
                    if (!got.isEmpty()) return bs;
                }
            }
        }
        return null;
    }

    /** 从指定背包原子取 1 个可放置方块 (extractItem 即扣, 不复制) */
    private static ItemStack takeOnePlaceable(IItemHandler inv, EntityMaid maid) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || isTool(s)) continue;
            if (blockStateOf(s) == null) continue;
            ItemStack got = inv.extractItem(i, 1, false);
            if (!got.isEmpty()) return got;
        }
        return ItemStack.EMPTY;
    }

    /** ItemStack → 方块状态 (可放置方块), 非方块/空气 → null */
    @javax.annotation.Nullable
    private static net.minecraft.world.level.block.state.BlockState blockStateOf(ItemStack s) {
        if (s.isEmpty()) return null;
        net.minecraft.world.level.block.Block blk = net.minecraft.world.level.block.Block.byItem(s.getItem());
        if (blk == net.minecraft.world.level.block.Blocks.AIR) return null;
        net.minecraft.world.level.block.state.BlockState bs = blk.defaultBlockState();
        if (bs.isAir()) return null;
        return bs;
    }

    /**
     * 容器是否还有空间 (输出箱) / 有内容 (输入箱).
     *
     * @return 输出箱: 剩余空位 > 0; 输入箱: 槽位内容 > 0
     */
    public static boolean hasSpace(ServerLevel world, BlockPos outputPos) {
        IItemHandler handler = getHandler(world, outputPos);
        if (handler == null) return false;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    /** 输入箱非空 (有工具可取) */
    public static boolean hasTool(ServerLevel world, BlockPos inputPos) {
        IItemHandler handler = getHandler(world, inputPos);
        if (handler == null) return false;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
    }

    /** 容器剩余空间估算 (空槽 + 可继续堆叠的格数) — 用于蔓延方向决策 (v79.62.1).
     *  <p>近似: 每槽算 1 单位空间 (空槽=1, 可堆叠满=0, 部分=0.5); 玩家实际堆叠空间由 depositBlocks 精确处理. */
    public static int freeSlots(ServerLevel world, BlockPos pos) {
        IItemHandler handler = getHandler(world, pos);
        if (handler == null) return 0;
        int free = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty()) free += 1;
            else if (s.getCount() < s.getMaxStackSize()) free += 1;   // 可堆叠算有空间
        }
        return free;
    }

    /** 女仆背包是否满 (无空槽且无可堆叠空间) — v79.62.1 用户裁定: 满则停挖提醒 */
    public static boolean backpackFull(EntityMaid maid) {
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return false;
        }
        return true;
    }

    /** 4 方向邻居 (N/S/E/W, 同 Y) */
    public static BlockPos neighbor(BlockPos pos, int index) {
        return switch (index) {
            case 0 -> pos.relative(Direction.NORTH);
            case 1 -> pos.relative(Direction.SOUTH);
            case 2 -> pos.relative(Direction.WEST);
            default -> pos.relative(Direction.EAST);
        };
    }
}
