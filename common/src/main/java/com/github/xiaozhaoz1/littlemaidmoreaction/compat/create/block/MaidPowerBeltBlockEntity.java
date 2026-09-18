package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.block;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlockEntityTypes;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlocks;
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
        addSurfaceMovement(signedSurfaceSpeed, 1f);
    }

    /** 接收表面速度 + 蛋糕应力倍率 (v79.62.1) — cakeMult = 2^蛋糕数 (1/2/4), 应力层乘倍 */
    public void addSurfaceMovement(float signedSurfaceSpeed, float cakeMult) {
        MaidPowerBeltBlockEntity controllerBE = isController() ? this : getControllerBE();
        if (controllerBE == null) {
            LittleMaidMoreAction.LOGGER.info("[MaidPowerBelt] addSurfaceMovement: no controller at {}", worldPosition);
            return;
        }
        LittleMaidMoreAction.LOGGER.info("[MaidPowerBelt] addSurfaceMovement speed={} cakeMult={} at {}", signedSurfaceSpeed, cakeMult, worldPosition);
        controllerBE.collectSurfaceMovement(signedSurfaceSpeed, cakeMult);
    }

    private void collectSurfaceMovement(float signedSurfaceSpeed, float cakeMult) {
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
        // ★ v79.72 (用户方案, 错题 #362): 配置 ≥0 ⇒ **在源头固定总应力** —— 不乘蛋糕, **也不受夹制** ✗
        //   原因: 下面那个 `getMaxStressCapacity()` = beltLength × (MAX_GENERATED_RPM × 4) = **段数 × 1024**
        //   ⇒ **一段皮带永远只能算到 1024** ✓ 这就是"配置 100000 却读 1024"的最后一环 ✓
        //   ⇒ 直接路径与表面链路从此**产出同一个值**(配置值) ⇒ 不再互相覆盖 ✓
        //   (用户裁定: 1024/2048/4096 这些固定值都是 LMA 自己写的 ⇒ 就在生成处检查配置, 别在写入仲裁层打补丁 ✓)
        int customStress = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.POWER_BELT_STRESS.get();
        if (customStress >= 0) {
            collectedStressCapacity = customStress;
            return;
        }
        // v79.62.1 蛋糕应力倍率: cakeMult = 2^蛋糕数 (1/2/4) — 应力 = RPM×4×cakeMult
        // (绕开 MAX_GENERATED_RPM=256 封顶对速度→应力的拖累, 1 蛋糕×2 / 2 蛋糕×4)
        collectedStressCapacity =
                Mth.clamp(collectedStressCapacity + getStressCapacityForRpm(speed) * cakeMult, 0, getMaxStressCapacity());
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

        // ★ v79.72 fix (错题 #360): 直接输出 (女仆 sprint 时的配置值) 在窗口内是**权威** ⇒ 本链路让位 ✓
        //   (否则配置的 100000 会被采样值覆盖成 1024 ✗ — 这就是"改了没变"的最后一层根因)
        if (level != null && level.getGameTime() - lastDirectOutputTick < DIRECT_OVERRIDE_TICKS)
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
        // ★ v79.72 (用户方案, 错题 #362 真根因): 配置 ≥0 ⇒ **不做量化, 也不夹上限** —— 直接以配置值为准 ✓
        //   ✗ 原来这里 `Mth.clamp(…, 0, getMaxStressCapacity())`, 而上限 = beltLength × (256×4) = **段数 × 1024**
        //   ⇒ 检测链路 (`applyDetectedSurfaceMovement` → 本方法) 算出的 100000 **在这里被压回 1024** ✓✓
        //   ⇒ 日志里那条 `capacity/每RPM=10.666667 ⇒ 总应力=1024.0` (= 1024/96) 就是它产出的 ✓ (与实测完全吻合)
        int customStress = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.POWER_BELT_STRESS.get();
        if (customStress >= 0) {
            return customStress;
        }
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
        // ★ v79.72 (用户方案, 错题 #362): **唯一写入口处强制配置值** —— 不管调用方算出来什么
        //   (direct 路径 / 表面速度链路 / 检测平均链路 / 以后新增的任何路径), 只要配置 ≥0, 就一律
        //   以「配置总应力 ÷ 转速」作为每 RPM 容量 ✓
        //   教训: 我先前只在 `collectSurfaceMovement` 一处加检查 ⇒ 仍有一条链路产出 `capacity=10.666667`
        //   (即 96×10.667=1024, = `STRESS_CAPACITY_PER_RPM`) 把值盖回去 ✗ ⇒ 改成在**字段写入点**统一强制 ✓
        int customStress = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.POWER_BELT_STRESS.get();
        if (customStress >= 0) {
            if (Mth.equal(speed, 0)) {
                capacity = 0;
            } else {
                capacity = customStress / Math.abs(speed);
            }
        }
        if (Mth.equal(generatedSpeed, speed) && Mth.equal(generatedCapacity, capacity))
            return;   // 值没变 ⇒ 直接返回 (调用方每 tick 调, 这里就是幂等闸门 ✓)

        generatedSpeed = speed;
        generatedCapacity = capacity;
        updateGeneratedRotation();
        // ★ v79.72 (错题 #359): **写字段 ≠ 生效** — Create 把应力容量缓存在**网络**里, 必须显式 dirty 才会重算。
        //   入口 = `KineticBlockEntity.networkDirty` (fact-forcing: javap 本地 create-1.20.1-6.0.8.jar 实证
        //   `public boolean networkDirty;` ✓, 另有 updateSpeed 只管转速路径 ✓)。
        //   原实现只写 generatedSpeed/generatedCapacity 不 dirty ⇒ 用户把"皮带发电应力"改成 100000 后,
        //   日志里字段已是 100000 ✓ 但 Create 护目镜仍显示 1024 (网络缓存的旧容量) ✗ —— 本行修的就是它 ✓
        networkDirty = true;
        // 日志按**用户看到的量** (总应力) 判定 — 转速/换算容量随蛋糕数抖动时不刷屏 (实测曾 600~5842 行 ✗)
        float totalStress = speed * capacity;
        if (!Mth.equal(lastLoggedTotalStress, totalStress)) {
            lastLoggedTotalStress = totalStress;
            LittleMaidMoreAction.LOGGER.info("[MaidPowerBelt] 输出变更: rpm={} capacity/每RPM={} ⇒ 总应力={}",
                    speed, capacity, totalStress);
        }
    }

    /** 上次打日志的总应力 (见上: 只在用户可见量变化时打 ✓) */
    private float lastLoggedTotalStress = Float.NaN;

    /**
     * 直接设置发电输出 (v79.62.1) — 绕开表面速度采样链路, 精确控制最终应力.
     *  Create 应力 = generatedSpeed(RPM) × generatedCapacity(每RPM应力).
     *  调用方传目标 RPM + 目标总应力, 内部换算 capacity = stress / rpm.
     *  非控制器段转发给 controller; 已在 controller 上则直接设置.
     */
    public void setDirectOutput(float rpm, float stress) {
        MaidPowerBeltBlockEntity controllerBE = isController() ? this : getControllerBE();
        if (controllerBE == null) return;
        // ★ v79.72 fix (错题 #360): **直接输出必须是权威 writer** —— 表面速度链路
        //   (`applyDetectedSurfaceMovement` → `setGeneratedOutput`) 每个检测周期都会覆盖它 ✗
        //   (实测: 配置 100000 写进去了, 但紧接着被采样值 1024 盖回去 ⇒ 护目镜永远 1024)
        //   ⇒ 记录"最后一次直接输出的时刻", 窗口内让表面链路 yield ✓ (她停了 sprint 后窗口过期, 采样链路自然接管 ✓)
        controllerBE.lastDirectOutputTick = controllerBE.level != null ? controllerBE.level.getGameTime() : 0L;
        if (Mth.equal(rpm, 0)) {
            controllerBE.setGeneratedOutput(0, 0);
            return;
        }
        controllerBE.setGeneratedOutput(rpm, stress / Math.abs(rpm));
    }

    /** 直接输出的权威窗口 (tick) — 窗口内表面速度链路不得覆盖 (女仆 sprint 期间每 tick 刷新 ⇒ 持续权威 ✓) */
    private static final int DIRECT_OVERRIDE_TICKS = 40;
    private long lastDirectOutputTick = Long.MIN_VALUE;

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

    /**
     * 皮带实际移动方向 (v79.62.1) — 对齐 Create {@code BeltBlockEntity.getMovementFacing()}:
     * 移动沿皮带轴向 (FACING 轴), 方向由速度正负决定:
     * {@code dir = (speed<0) ^ (axis==X) ? NEGATIVE : POSITIVE}.
     * 女仆身体应朝此方向 (模拟在跑步机上跑步, 而非跟随玩家).
     */
    public Direction getMovementFacing() {
        Direction facing = getBeltFacing();
        Direction.Axis axis = facing.getAxis();
        boolean negBySpeed = getBeltMovementSpeed() < 0;
        boolean negByAxisX = axis == Direction.Axis.X;
        Direction.AxisDirection dir = (negBySpeed ^ negByAxisX)
                ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE;
        return Direction.fromAxisAndDirection(axis, dir);
    }

    @Override
    public AABB createRenderBoundingBox() {
        return isController() ? super.createRenderBoundingBox().inflate(beltLength + 1) : super.createRenderBoundingBox();
    }

//? if 1.20.1 {
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
//?} else {
    /** Create 1.21 SmartBlockEntity.saveAdditional final — NBT 钩子 = write/read 3参 (Provider, boolean) */
    @Override
    protected void write(CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (controller != null)
            compound.put("Controller", net.minecraft.nbt.NbtUtils.writeBlockPos(controller));
        compound.putBoolean("IsController", isController());
        compound.putInt("Length", beltLength);
        compound.putInt("Index", index);
        NBTHelper.writeEnum(compound, "Casing", casing);
        compound.putBoolean("Covered", covered);
        if (isController()) {
            compound.putFloat("GeneratedSpeed", generatedSpeed);
            compound.putFloat("GeneratedCapacity", generatedCapacity);
        }
    }

    @Override
    protected void read(CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        if (compound.getBoolean("IsController"))
            controller = worldPosition;

        if (!wasMoved) {
            if (!isController() && compound.contains("Controller"))
                controller = net.minecraft.nbt.NbtUtils.readBlockPos(compound, "Controller").orElse(null);
            index = compound.getInt("Index");
            beltLength = compound.getInt("Length");
//?}
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

//? if 1.20.1 {
        if (!clientPacket)
            return;
//?}
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
