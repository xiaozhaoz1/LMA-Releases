package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlock;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.block.MaidPowerBeltBlockEntity;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

import java.util.List;

/**
 * 女仆跑步发电 IO 层 — 皮带搜索 + 方块转换 + 食物管理。
 */
public final class RunningBeltService {
    private static final int SEARCH_RANGE = 3;
    private static final ResourceLocation BELT_ID = ResourceLocation.fromNamespaceAndPath("create", "belt");

    private RunningBeltService() {}

    // ── Input ──

    public static BlockPos findNearestBelt(Level level, BlockPos center) {
        for (int dr = 0; dr <= SEARCH_RANGE; dr++) {
            for (int dx = -dr; dx <= dr; dx++) {
                for (int dz = -dr; dz <= dr; dz++) {
                    if (Math.abs(dx) != dr && Math.abs(dz) != dr) continue;
                    BlockPos pos = center.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = pos.offset(0, dy, 0);
                        BlockState state = level.getBlockState(p);
                        if (isBelt(state) && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL) {
                            return p.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos getBeltControllerPos(Level level, BlockPos segmentPos) {
        BeltBlockEntity segment = BeltHelper.getSegmentBE(level, segmentPos);
        if (segment == null) return null;
        BeltBlockEntity controller = BeltHelper.getControllerBE(level, segment.getBlockPos());
        return controller != null ? controller.getBlockPos() : null;
    }

    /** 找皮带链中点 — 让女仆导航到中间不冲过头 */
    public static BlockPos findBeltMidpoint(Level level, BlockPos segmentPos) {
        BlockPos controllerPos = getBeltControllerPos(level, segmentPos);
        if (controllerPos == null) return segmentPos;
        List<BlockPos> chain = BeltBlock.getBeltChain(level, controllerPos);
        if (chain.size() < 2) return segmentPos;
        return chain.get(chain.size() / 2);
    }

    // ── Compute ──

    public static boolean isMaidOnBelt(net.minecraft.world.entity.Entity maid, BlockPos pos) {
        // 加水平约束 — 原仅查 Y, 水平走开同高度仍算"在皮带上" → 永不 revert 乱跑
        if (Math.abs(maid.getBlockX() - pos.getX()) > 1
                || Math.abs(maid.getBlockZ() - pos.getZ()) > 1) {
            return false;
        }
        return maid.getY() - 0.25f >= pos.getY();
    }

    // ── 食物 IO (Input 纯查询 + Output 副作用) ──

    /** 食物槽位记录 */
    public record FoodSlot(int slotIndex, ItemStack stack) {}

    /** 可食判断双平台 (1.21 无 isEdible — DataComponents.FOOD) */
    private static boolean hasFood(ItemStack stack) {
//? if 1.20.1 {
        return stack.getItem().isEdible();
//?} else {
        return stack.has(net.minecraft.core.component.DataComponents.FOOD);
//?}
    }

    /**
     * Input: 在女仆背包中查找第一个可食物品。
     * @return FoodSlot 或 null (无食物)
     */
    public static FoodSlot findFoodItem(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && hasFood(s)) {
                return new FoodSlot(i, s.copy());
            }
        }
        return null;
    }

    /**
     * Output: 消耗指定槽位的一个食物。
     * 调用 EntityMaid.eat() → 恢复饱食度 + 药水效果 + 音效。
     * eat() 可能或可能不shrink，显式shrink确保消耗。
     * @return true 如果成功消耗
     */
    public static boolean consumeFood(EntityMaid maid, int slotIndex) {
        var inv = maid.getAvailableInv(true);
        ItemStack s = inv.getStackInSlot(slotIndex);
        if (s.isEmpty() || !hasFood(s)) return false;
        maid.eat(maid.level(), s);
        s.shrink(1);
        return true;
    }

    // ── Output: 方块转换 ──

    public static boolean convertToMaidPowerBelt(Level level, BlockPos clickedPos) {
        BlockPos controllerPos = findBeltController(level, clickedPos);
        if (controllerPos == null) return false;

        List<BlockPos> beltChain = BeltBlock.getBeltChain(level, controllerPos);
        if (beltChain.size() < 2) return false;
        for (BlockPos beltPos : beltChain) {
            if (!level.isLoaded(beltPos)) return false;
            BlockState s = level.getBlockState(beltPos);
            if (!isBelt(s) || s.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
                return false;
        }

        BeltBlockEntity controllerBE = BeltHelper.getSegmentBE(level, controllerPos);
        if (controllerBE != null && controllerBE.isController() && controllerBE.getInventory() != null)
            controllerBE.getInventory().ejectAll();

        for (BlockPos beltPos : beltChain) {
            BeltBlockEntity belt = BeltHelper.getSegmentBE(level, beltPos);
            if (belt == null) continue;
            belt.detachKinetics();
            belt.invalidateItemHandler();
            belt.beltLength = 0;
        }

        for (BlockPos beltPos : beltChain) {
            BlockState oldState = level.getBlockState(beltPos);
            BlockState newState = LmaBlocks.MAID_POWER_BELT.get().defaultBlockState()
                    .setValue(MaidPowerBeltBlock.SLOPE, oldState.getValue(BeltBlock.SLOPE))
                    .setValue(MaidPowerBeltBlock.PART, oldState.getValue(BeltBlock.PART))
                    .setValue(MaidPowerBeltBlock.HORIZONTAL_FACING, oldState.getValue(BeltBlock.HORIZONTAL_FACING))
                    .setValue(MaidPowerBeltBlock.CASING, false);
            newState = ProperWaterloggedBlock.withWater(level, newState, beltPos);
            level.setBlock(beltPos, newState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
            level.levelEvent(2001, beltPos, Block.getId(newState));
        }

        MaidPowerBeltBlock.initBelt(level, controllerPos);
        return true;
    }

    public static boolean revertToRegularBelt(Level level, BlockPos clickedPos) {
        BlockPos controllerPos = findMaidPowerBeltController(level, clickedPos);
        if (controllerPos == null) return false;

        List<BlockPos> beltChain = MaidPowerBeltBlock.getBeltChain(level, controllerPos);
        if (beltChain.isEmpty()) return false;

        for (BlockPos beltPos : beltChain) {
            MaidPowerBeltBlockEntity be = MaidPowerBeltBlock.getSegmentBE(level, beltPos);
            if (be != null) be.detachKinetics();
        }

//? if 1.20.1 {
        Block beltBlock = ForgeRegistries.BLOCKS.getValue(BELT_ID);
//?} else {
        Block beltBlock = BuiltInRegistries.BLOCK.get(BELT_ID);   // 1.21 getValue→get
//?}
        if (beltBlock == null) return false;

        for (BlockPos beltPos : beltChain) {
            BlockState oldState = level.getBlockState(beltPos);
            if (!MaidPowerBeltBlock.isMaidPowerBelt(oldState)) continue;

            BlockState newState = beltBlock.defaultBlockState()
                    .setValue(BeltBlock.SLOPE, oldState.getValue(MaidPowerBeltBlock.SLOPE))
                    .setValue(BeltBlock.PART, oldState.getValue(MaidPowerBeltBlock.PART))
                    .setValue(BeltBlock.HORIZONTAL_FACING, oldState.getValue(MaidPowerBeltBlock.HORIZONTAL_FACING));
            newState = ProperWaterloggedBlock.withWater(level, newState, beltPos);
            level.setBlock(beltPos, newState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
            level.levelEvent(2001, beltPos, Block.getId(newState));
        }

        BeltBlock.initBelt(level, controllerPos);
        return true;
    }

    // ── 内部工具 ──

    private static boolean isBelt(BlockState state) {
        return state.getBlock() instanceof BeltBlock;
    }

    private static BlockPos findBeltController(Level level, BlockPos pos) {
        BlockPos currentPos = pos;
        int limit = 1000;
        while (limit-- > 0) {
            if (!level.isLoaded(currentPos)) return null;
            BlockState currentState = level.getBlockState(currentPos);
            if (!isBelt(currentState) || currentState.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
                return null;
            BlockPos next = BeltBlock.nextSegmentPosition(currentState, currentPos, false);
            if (next == null) return currentPos;
            currentPos = next;
        }
        return null;
    }

    private static BlockPos findMaidPowerBeltController(Level level, BlockPos pos) {
        BlockPos currentPos = pos;
        int limit = 1000;
        while (limit-- > 0) {
            if (!level.isLoaded(currentPos)) return null;
            BlockState currentState = level.getBlockState(currentPos);
            if (!MaidPowerBeltBlock.isMaidPowerBelt(currentState)) return null;
            BlockPos next = MaidPowerBeltBlock.nextSegmentPosition(currentState, currentPos, false);
            if (next == null) return currentPos;
            currentPos = next;
        }
        return null;
    }
}
