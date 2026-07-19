package littlemaidmoreaction.littlemaidmoreaction.compat.create.block;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import littlemaidmoreaction.littlemaidmoreaction.init.LmaBlockEntityTypes;
import littlemaidmoreaction.littlemaidmoreaction.init.LmaBlocks;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 女仆发电皮带方块实体 — 仅当女仆在皮带上跑步时产生旋转动力。
 *
 * <p>继承 {@link GeneratingKineticBlockEntity}，通过 {@link #addSurfaceMovement(float)}
 * 接收女仆在皮带表面的移动速度并转换为 RPM。与 PowerBeltBlockEntity 的核心逻辑一致，
 * 但使用常量代替配置值，且不依赖 Create-Biotech。
 *
 * <p>设计参考: PowerBeltBlockEntity (Create-Biotech)
 */
public class MaidPowerBeltBlockEntity extends GeneratingKineticBlockEntity {

    public static final float MIN_SURFACE_SPEED = 1.0E-4f;

    private static final float GENERATED_RPM_STEP = 4f;
    private static final float MAX_GENERATED_RPM = 256f;
    private static final float STRESS_CAPACITY_PER_RPM = 4f;
    private static final float SURFACE_SPEED_TO_RPM = 480f;
    private static final int SURFACE_SPEED_DETECTION_INTERVAL = 10;

    public int beltLength;
    public int index;
    protected BlockPos controller;

    public CasingType casing;
    public boolean covered;

    private long lastMovementGameTime = Long.MIN_VALUE;
    private long nextDetectionGameTime = Long.MIN_VALUE;
    private float collectedGeneratedSpeed;
    private float collectedStressCapacity;
    private float collectedDetectionGeneratedSpeed;
    private float collectedDetectionStressCapacity;
    private int collectedDetectionTicks;
    private float generatedSpeed;
    private float generatedCapacity;

    public MaidPowerBeltBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        casing = CasingType.NONE;
    }

    public MaidPowerBeltBlockEntity(BlockPos pos, BlockState state) {
        this(LmaBlockEntityTypes.MAID_POWER_BELT.get(), pos, state);
    }

    @Override
    public void tick() {
        if (beltLength == 0)
            MaidPowerBeltBlock.initBelt(level, worldPosition);

        super.tick();

        if (level == null || level.isClientSide)
            return;
        if (!getBlockState().is(LmaBlocks.MAID_POWER_BELT.get()))
            return;
        if (!isController())
            return;

        sampleSurfaceMovementBefore(level.getGameTime());
    }

    /** 接收女仆在皮带表面的移动速度。路由到控制器 BE。 */
    public void addSurfaceMovement(float signedSurfaceSpeed) {
        MaidPowerBeltBlockEntity controllerBE = isController() ? this : getControllerBE();
        if (controllerBE == null) {
            LittleMaidMoreAction.LOGGER.info("[MaidPowerBelt] addSurfaceMovement: no controller at {}", worldPosition);
            return;
        }
        LittleMaidMoreAction.LOGGER.info("[MaidPowerBelt] addSurfaceMovement speed={} at {}", signedSurfaceSpeed, worldPosition);
        controllerBE.collectSurfaceMovement(signedSurfaceSpeed);
    }

    private void collectSurfaceMovement(float signedSurfaceSpeed) {
        if (level == null || level.isClientSide)
            return;
        if (Math.abs(signedSurfaceSpeed) < MIN_SURFACE_SPEED)
            return;

        float speed = surfaceSpeedToGeneratedRpm(signedSurfaceSpeed);
        if (speed == 0)
            return;

        long gameTime = level.getGameTime();
        sampleSurfaceMovementBefore(gameTime);
        if (gameTime != lastMovementGameTime) {
            lastMovementGameTime = gameTime;
            collectedGeneratedSpeed = 0;
            collectedStressCapacity = 0;
        }

        collectedGeneratedSpeed = getStrongerSpeed(collectedGeneratedSpeed, speed);
        collectedStressCapacity =
                Mth.clamp(collectedStressCapacity + getStressCapacityForRpm(speed), 0, getMaxStressCapacity());
    }

    private void sampleSurfaceMovementBefore(long gameTime) {
        if (nextDetectionGameTime == Long.MIN_VALUE)
            nextDetectionGameTime = gameTime;

        while (nextDetectionGameTime < gameTime) {
            float speed = nextDetectionGameTime == lastMovementGameTime ? collectedGeneratedSpeed : 0;
            float stressCapacity = nextDetectionGameTime == lastMovementGameTime ? collectedStressCapacity : 0;
            collectedDetectionGeneratedSpeed += speed;
            collectedDetectionStressCapacity += stressCapacity;
            collectedDetectionTicks++;

            if (nextDetectionGameTime == lastMovementGameTime) {
                collectedGeneratedSpeed = 0;
                collectedStressCapacity = 0;
            }

            nextDetectionGameTime++;

            if (collectedDetectionTicks >= SURFACE_SPEED_DETECTION_INTERVAL)
                applyDetectedSurfaceMovement();
        }
    }

    private void applyDetectedSurfaceMovement() {
        float speed = roundToGeneratedRpmStep(collectedDetectionGeneratedSpeed / collectedDetectionTicks);
        float generatedStressCapacity =
                roundToGeneratedStressStep(collectedDetectionStressCapacity / collectedDetectionTicks);
        float capacity = speed == 0 ? 0 : generatedStressCapacity / Math.abs(speed);

        collectedDetectionGeneratedSpeed = 0;
        collectedDetectionStressCapacity = 0;
        collectedDetectionTicks = 0;

        if (!shouldApplyDetectedOutput(speed, capacity))
            return;

        setGeneratedOutput(speed, capacity);
    }

    private boolean shouldApplyDetectedOutput(float speed, float capacity) {
        if (Mth.equal(generatedSpeed, speed))
            return !Mth.equal(generatedCapacity, capacity);
        if (generatedSpeed == 0 || speed == 0)
            return true;

        float difference = Math.abs(speed - generatedSpeed);
        return difference > GENERATED_RPM_STEP || Mth.equal(difference, GENERATED_RPM_STEP);
    }

    private float surfaceSpeedToGeneratedRpm(float signedSurfaceSpeed) {
        Direction facing = getBlockState().getValue(MaidPowerBeltBlock.HORIZONTAL_FACING);
        return roundToGeneratedRpmStep(-signedSurfaceSpeed * SURFACE_SPEED_TO_RPM / getDirectionFactor(facing));
    }

    private float getStrongerSpeed(float currentSpeed, float candidateSpeed) {
        float currentMagnitude = Math.abs(currentSpeed);
        float candidateMagnitude = Math.abs(candidateSpeed);
        if (candidateMagnitude > currentMagnitude && !Mth.equal(candidateMagnitude, currentMagnitude))
            return candidateSpeed;
        if (Mth.equal(candidateMagnitude, currentMagnitude) && currentSpeed != 0
                && Math.signum(candidateSpeed) == Math.signum(generatedSpeed))
            return candidateSpeed;
        return currentSpeed == 0 ? candidateSpeed : currentSpeed;
    }

    private static float roundToGeneratedRpmStep(float speed) {
        float magnitude = Mth.clamp(Math.round(Math.abs(speed) / GENERATED_RPM_STEP) * GENERATED_RPM_STEP, 0,
                MAX_GENERATED_RPM);
        return Math.copySign(magnitude, speed);
    }

    private static float getStressCapacityForRpm(float rpm) {
        return Math.abs(rpm) * STRESS_CAPACITY_PER_RPM;
    }

    private float roundToGeneratedStressStep(float stressCapacity) {
        float generatedStressStep = GENERATED_RPM_STEP * STRESS_CAPACITY_PER_RPM;
        if (generatedStressStep <= 0)
            return 0;
        float capacity = Math.round(stressCapacity / generatedStressStep) * generatedStressStep;
        return Mth.clamp(capacity, 0, getMaxStressCapacity());
    }

    private float getMaxStressCapacity() {
        return Math.max(0, beltLength) * getMaxStressCapacityPerSegment();
    }

    private static float getMaxStressCapacityPerSegment() {
        return MAX_GENERATED_RPM * STRESS_CAPACITY_PER_RPM;
    }

    private static float getDirectionFactor(Direction facing) {
        int factor = facing.getAxisDirection().getStep();
        if (facing.getAxis() == Direction.Axis.X)
            factor *= -1;
        return factor;
    }

    private void setGeneratedOutput(float speed, float capacity) {
        if (Mth.equal(generatedSpeed, speed) && Mth.equal(generatedCapacity, capacity))
            return;

        generatedSpeed = speed;
        generatedCapacity = capacity;
        updateGeneratedRotation();
    }

    @Override
    public float getGeneratedSpeed() {
        if (!isController() || !getBlockState().is(LmaBlocks.MAID_POWER_BELT.get()))
            return 0;
        return generatedSpeed;
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = !isController() || generatedSpeed == 0 ? 0 : generatedCapacity;
        return lastCapacityProvided;
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = 0;
        return 0;
    }

    @Override
    public void clearKineticInformation() {
        super.clearKineticInformation();
        beltLength = 0;
        index = 0;
        controller = null;
        lastMovementGameTime = Long.MIN_VALUE;
        nextDetectionGameTime = Long.MIN_VALUE;
        collectedGeneratedSpeed = 0;
        collectedStressCapacity = 0;
        collectedDetectionGeneratedSpeed = 0;
        collectedDetectionStressCapacity = 0;
        collectedDetectionTicks = 0;
        generatedSpeed = 0;
        generatedCapacity = 0;
    }

    public boolean hasPulley() {
        return getBlockState().is(LmaBlocks.MAID_POWER_BELT.get())
                && getBlockState().getValue(MaidPowerBeltBlock.PART) != BeltPart.MIDDLE;
    }

    public MaidPowerBeltBlockEntity getControllerBE() {
        if (controller == null || level == null || !level.isLoaded(controller))
            return null;
        BlockEntity be = level.getBlockEntity(controller);
        return be instanceof MaidPowerBeltBlockEntity powerBelt ? powerBelt : null;
    }

    public void setController(BlockPos controller) {
        this.controller = controller;
    }

    public BlockPos getController() {
        return controller == null ? worldPosition : controller;
    }

    public boolean isController() {
        return controller != null && worldPosition.equals(controller);
    }

    public Direction getBeltFacing() {
        return getBlockState().getValue(MaidPowerBeltBlock.HORIZONTAL_FACING);
    }

    public float getBeltMovementSpeed() {
        return getSpeed() / SURFACE_SPEED_TO_RPM;
    }

    @Override
    public AABB createRenderBoundingBox() {
        return isController() ? super.createRenderBoundingBox().inflate(beltLength + 1) : super.createRenderBoundingBox();
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        if (controller != null)
            compound.put("Controller", NbtUtils.writeBlockPos(controller));
        compound.putBoolean("IsController", isController());
        compound.putInt("Length", beltLength);
        compound.putInt("Index", index);
        NBTHelper.writeEnum(compound, "Casing", casing);
        compound.putBoolean("Covered", covered);
        if (isController()) {
            compound.putFloat("GeneratedSpeed", generatedSpeed);
            compound.putFloat("GeneratedCapacity", generatedCapacity);
        }
        super.write(compound, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

        if (compound.getBoolean("IsController"))
            controller = worldPosition;

        if (!wasMoved) {
            if (!isController() && compound.contains("Controller"))
                controller = NbtUtils.readBlockPos(compound.getCompound("Controller"));
            index = compound.getInt("Index");
            beltLength = compound.getInt("Length");
        }

        if (compound.contains("GeneratedSpeed")) {
            generatedSpeed = compound.getFloat("GeneratedSpeed");
            generatedCapacity = compound.getFloat("GeneratedCapacity");
        } else if (isController() && !Mth.equal(lastCapacityProvided, 0) && !Mth.equal(getTheoreticalSpeed(), 0)) {
            generatedSpeed = getTheoreticalSpeed();
            generatedCapacity = lastCapacityProvided;
        }

        CasingType casingBefore = casing;
        boolean coverBefore = covered;
        casing = NBTHelper.readEnum(compound, "Casing", CasingType.class);
        covered = compound.getBoolean("Covered");

        if (!clientPacket)
            return;
        if (casingBefore == casing && coverBefore == covered)
            return;
        if (!isVirtual())
            requestModelDataUpdate();
        if (hasLevel())
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
    }

    @Override
    protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
        return false;
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
                                      boolean connectedViaAxes, boolean connectedViaCogs) {
        if (target instanceof MaidPowerBeltBlockEntity belt && !connectedViaAxes)
            return getController().equals(belt.getController()) ? 1 : 0;
        return 0;
    }

    @Override
    protected boolean isNoisy() {
        return false;
    }
}
