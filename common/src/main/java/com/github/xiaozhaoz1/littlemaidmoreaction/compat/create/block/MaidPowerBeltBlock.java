package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltShapes;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlockEntityTypes;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
//? if 1.20.1 {
import net.minecraft.world.level.pathfinder.BlockPathTypes;
//?} else {
import net.minecraft.world.level.pathfinder.PathType;
//?}
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

/**
 * 女仆发电皮带方块 — 仅女仆跑步时产生旋转动力。
 */
public class MaidPowerBeltBlock extends HorizontalKineticBlock
        implements IBE<MaidPowerBeltBlockEntity>, ProperWaterloggedBlock {

    public static final Property<BeltSlope> SLOPE = BeltBlock.SLOPE;
    public static final Property<BeltPart> PART = BeltBlock.PART;
    public static final BooleanProperty CASING = BeltBlock.CASING;

    public MaidPowerBeltBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SLOPE, BeltSlope.HORIZONTAL)
                .setValue(PART, BeltPart.PULLEY)
                .setValue(CASING, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState, newState)
                && oldState.getValue(PART) == newState.getValue(PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferredSide = getPreferredHorizontalFacing(context);
        Direction facing = preferredSide == null ? context.getHorizontalDirection().getOpposite()
                : preferredSide.getClockWise();
        return withWater(defaultBlockState().setValue(HORIZONTAL_FACING, facing), context);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        if (face.getAxis() != getRotationAxis(state))
            return false;
        return getBlockEntityOptional(world, pos).map(MaidPowerBeltBlockEntity::hasPulley).orElse(false);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        if (state.getValue(SLOPE) == BeltSlope.SIDEWAYS)
            return Axis.Y;
        return state.getValue(HORIZONTAL_FACING).getClockWise().getAxis();
    }

//? if 1.20.1 {
    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target,
                                        BlockGetter world, BlockPos pos, Player player) {
//?} else {
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState state) {
//?}
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return super.getDrops(state, builder);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter world, Entity entity) {
        super.updateEntityAfterFallOn(world, entity);
        BlockPos entityPosition = entity.blockPosition();
        BlockPos beltPos = null;

        if (isMaidPowerBelt(world.getBlockState(entityPosition)))
            beltPos = entityPosition;
        else if (isMaidPowerBelt(world.getBlockState(entityPosition.below())))
            beltPos = entityPosition.below();
        if (beltPos == null || !(world instanceof Level level))
            return;

        entityInside(world.getBlockState(beltPos), level, beltPos, entity);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof Player)
            return;
        captureSurfaceMovement(state, level, pos, entity);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
    }

    // ── 核心: 表面运动捕获 (仅 EntityMaid + running_belt 任务) ──

    private static void captureSurfaceMovement(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof EntityMaid maid))
            return;
        String taskType = LmaTaskTypeRegistry.extractTaskType(maid.getTask().getUid().getPath());
        if (!"running_belt".equals(taskType))
            return;
        if (!isMaidPowerBelt(state) || state.getValue(SLOPE) != BeltSlope.HORIZONTAL)
            return;
        if (!isEntityOnBeltSurface(pos, entity))
            return;

        // v79.62.1 蛋糕加成 (用户裁定 0→1024 / 1→2048 / 2→4096):
        //   直接设最终输出, 绕开表面速度采样/双通道累加链路.
        //   0/1/2 蛋糕 → RPM 96/192/256, 应力 1024/2048/4096.
        if (maid.isSprinting()) {
            // 蛋糕检测: 女仆前方 1~3 格 × 横向 ±1 (蛋糕放女仆面前地上)
            net.minecraft.core.Direction maidFacing = net.minecraft.core.Direction.fromYRot(maid.getYRot());
            int cakes = Math.min(countCakesAround(level, maid.blockPosition(), maidFacing), 2);
            float rpm = cakes == 0 ? 96f : (cakes == 1 ? 192f : 256f);
            float stress = cakes == 0 ? 1024f : (cakes == 1 ? 2048f : 4096f);
            // ★ v79.63.12 (用户裁定): **自定义应力容量** — 全局配置 ≥0 时覆盖上式 (不再随蛋糕变 ✓),
            //   -1 (默认) 保持原公式 ✓; **转速仍由蛋糕控制** ✓ (用户明确: 其他需要 mixin 的不做 ✗)
            int customStress = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.POWER_BELT_STRESS.get();
            if (customStress >= 0) {
                stress = customStress;
            }
            if (level.isClientSide) return;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MaidPowerBeltBlockEntity powerBelt) {
                // v79.72: 日志已移入 BE 的"值真的变了"分支 (原每 sprint tick 都打 ⇒ 实测刷 5842 行 ✗)
                powerBelt.setDirectOutput(rpm, stress);
            }
            return;
        }
        // 非 sprint (女仆走/被推) — 保持旧表面速度链路
        Vec3 beltAxis = Vec3.atLowerCornerOf(state.getValue(HORIZONTAL_FACING).getNormal());
        Vec3 tickMovement = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        Vec3 motion = entity.getDeltaMovement();
        double movedSurfaceSpeed = tickMovement.x * beltAxis.x + tickMovement.z * beltAxis.z;
        double motionSurfaceSpeed = motion.x * beltAxis.x + motion.z * beltAxis.z;
        float surfaceSpeed = (float)(Math.abs(movedSurfaceSpeed) >= MaidPowerBeltBlockEntity.MIN_SURFACE_SPEED
                ? movedSurfaceSpeed : motionSurfaceSpeed);
        if (Math.abs(surfaceSpeed) < MaidPowerBeltBlockEntity.MIN_SURFACE_SPEED)
            return;

        entity.hurtMarked = true;
        if (level.isClientSide) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MaidPowerBeltBlockEntity powerBelt) {
            powerBelt.addSurfaceMovement(surfaceSpeed);
        }
    }

    /** 统计女仆前方蛋糕数 (v79.62.1 用户裁定) —
     *  从女仆面前 1~3 格 × 横向 ±1 的 3×3 平面 (蛋糕放女仆视角前方地上, 不含脚下).
     *  上限 2 块 (调用方 Math.min(cakes,2) 截断). */
    public static int countCakesAround(Level level, BlockPos maidPos, net.minecraft.core.Direction front) {
        int count = 0;
        net.minecraft.core.Direction left = front.getCounterClockWise();
        for (int dist = 1; dist <= 3; dist++) {
            for (int side = -1; side <= 1; side++) {
                BlockPos p = maidPos
                        .relative(front, dist)
                        .relative(left, side);
                if (!level.isLoaded(p)) continue;
                if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.CakeBlock) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isEntityOnBeltSurface(BlockPos pos, Entity entity) {
        return entity.getY() - .25f >= pos.getY();
    }

    // ── 交互 ──

//? if 1.20.1 {
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
//?} else {
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
                                             BlockHitResult hit) {
        return InteractionResult.PASS;
//?}
    }

    // ── 皮带链管理 ──

    public static void initBelt(Level world, BlockPos pos) {
        if (world == null || world.isClientSide)
            return;

        BlockState state = world.getBlockState(pos);
        if (!isMaidPowerBelt(state))
            return;

        int limit = 1000;
        BlockPos currentPos = pos;
        while (limit-- > 0) {
            BlockState currentState = world.getBlockState(currentPos);
            if (!isMaidPowerBelt(currentState))
                return;
            BlockPos nextSegmentPosition = nextSegmentPosition(currentState, currentPos, false);
            if (nextSegmentPosition == null)
                break;
            if (!world.isLoaded(nextSegmentPosition))
                return;
            currentPos = nextSegmentPosition;
        }

        int index = 0;
        List<BlockPos> beltChain = getBeltChain(world, currentPos);
        if (beltChain.size() < 2) return;

        for (BlockPos beltPos : beltChain) {
            BlockEntity blockEntity = world.getBlockEntity(beltPos);
            BlockState currentState = world.getBlockState(beltPos);
            if (!(blockEntity instanceof MaidPowerBeltBlockEntity be) || !isMaidPowerBelt(currentState))
                return;

            be.setController(currentPos);
            be.beltLength = beltChain.size();
            be.index = index;
            be.attachKinetics();
            be.setChanged();
            be.sendData();
            index++;
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbourState,
                                   LevelAccessor world, BlockPos pos, BlockPos neighbourPos) {
        updateWater(world, state, pos);
        return state;
    }

    // ── Belt 链遍历 ──

    public static List<BlockPos> getBeltChain(LevelAccessor world, BlockPos controllerPos) {
        List<BlockPos> positions = new LinkedList<>();
        BlockState blockState = world.getBlockState(controllerPos);
        if (!isMaidPowerBelt(blockState))
            return positions;

        int limit = 1000;
        BlockPos current = controllerPos;
        while (limit-- > 0 && current != null) {
            BlockState state = world.getBlockState(current);
            if (!isMaidPowerBelt(state))
                break;
            positions.add(current);
            current = nextSegmentPosition(state, current, true);
        }
        return positions;
    }

    public static BlockPos nextSegmentPosition(BlockState state, BlockPos pos, boolean forward) {
        Direction direction = state.getValue(HORIZONTAL_FACING);
        BeltSlope slope = state.getValue(SLOPE);
        BeltPart part = state.getValue(PART);
        int offset = forward ? 1 : -1;

        if (part == BeltPart.END && forward || part == BeltPart.START && !forward)
            return null;
        if (slope == BeltSlope.VERTICAL)
            return pos.above(direction.getAxisDirection() == AxisDirection.POSITIVE ? offset : -offset);
        pos = pos.relative(direction, offset);
        if (slope != BeltSlope.HORIZONTAL && slope != BeltSlope.SIDEWAYS)
            return pos.above(slope == BeltSlope.UPWARD ? offset : -offset);
        return pos;
    }

    // ── 状态定义 ──

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(SLOPE, PART, CASING, WATERLOGGED);
        super.createBlockStateDefinition(builder);
    }

    @Override
//? if 1.20.1 {
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob entity) {
        return BlockPathTypes.WALKABLE; // 用静态常量，防EnumMap越界崩溃
    }
//?} else {
    public net.minecraft.world.level.pathfinder.PathType getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob entity) {
        return net.minecraft.world.level.pathfinder.PathType.WALKABLE; // 1.21 BlockPathTypes → PathType
    }
//?}

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return BeltShapes.getShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getBlock() != this)
            return Shapes.empty();
        return BeltShapes.getCollisionShape(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(CASING) ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        BlockState rotated = super.rotate(state, rot);
        if (state.getValue(SLOPE) != BeltSlope.VERTICAL)
            return rotated;
        if (state.getValue(HORIZONTAL_FACING).getAxisDirection()
                != rotated.getValue(HORIZONTAL_FACING).getAxisDirection()) {
            if (state.getValue(PART) == BeltPart.START)
                return rotated.setValue(PART, BeltPart.END);
            if (state.getValue(PART) == BeltPart.END)
                return rotated.setValue(PART, BeltPart.START);
        }
        return rotated;
    }

    @Override
    //? if 1.20.1 {
    public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
//?} else {
    public boolean isPathfindable(BlockState state, PathComputationType type) {
//?}
        return true; // 女仆需寻路走到皮带上
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    // ── BlockState 检测 ──

    public static boolean isMaidPowerBelt(BlockState state) {
        return state.is(LmaBlocks.MAID_POWER_BELT.get());
    }

    @Nullable
    public static MaidPowerBeltBlockEntity getSegmentBE(BlockGetter world, BlockPos pos) {
        if (world instanceof Level level && !level.isLoaded(pos))
            return null;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof MaidPowerBeltBlockEntity powerBelt ? powerBelt : null;
    }

    @Nullable
    public static MaidPowerBeltBlockEntity getControllerBE(BlockGetter world, BlockPos pos) {
        MaidPowerBeltBlockEntity segment = getSegmentBE(world, pos);
        if (segment == null)
            return null;
        return getSegmentBE(world, segment.getController());
    }

    // ── IBE ──

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaidPowerBeltBlockEntity(pos, state);
    }

    @Override
    public Class<MaidPowerBeltBlockEntity> getBlockEntityClass() {
        return MaidPowerBeltBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MaidPowerBeltBlockEntity> getBlockEntityType() {
        return LmaBlockEntityTypes.MAID_POWER_BELT.get();
    }
}
