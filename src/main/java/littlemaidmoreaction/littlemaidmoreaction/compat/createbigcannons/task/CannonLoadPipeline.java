package littlemaidmoreaction.littlemaidmoreaction.compat.createbigcannons.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.navigation.NavigationMemory;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.runtime.TaskStateMachine;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 速射炮闩装填管线 — 多炮架+Worm清膛+装填顺序验证+目光跟踪+定期重扫。
 *
 * <p>loaded标记持久化防止重复装填; 每10秒全清loaded标记重扫以检测已发射的炮。
 */
public final class CannonLoadPipeline extends TaskStateMachine<CannonLoadPipeline.State> {

    enum State { SEARCHING, MOVING, OPENING, CLEARING, LOADING, CLOSING }

    private static final String KEY_STEP = "load_step";
    private static final String KEY_MOUNT = "mount_pos";
    private static final String KEY_CD_MOUNT = "cd_mount";
    private static final String KEY_LOADED = "loaded_mounts";
    private static final String KEY_CLEAR_STEP = "clear_step";
    private static final String KEY_NAV_MODE = "nav_mode";
    private static final String KEY_LAST_CLEAR = "last_clear_all";
    private static final int MAX_SUB = 3;
    private static final long CLEAR_ALL_INTERVAL = 60; // 每3秒全清loaded标记重扫

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "cannon_load"; }
    @Override public boolean needsGameTick() { return true; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.SEARCHING, Set.of(State.MOVING),
            State.MOVING,    Set.of(State.OPENING, State.SEARCHING),
            State.OPENING,   Set.of(State.CLEARING, State.LOADING, State.SEARCHING),
            State.CLEARING,  Set.of(State.OPENING, State.SEARCHING),
            State.LOADING,   Set.of(State.CLOSING, State.SEARCHING),
            State.CLOSING,   Set.of(State.SEARCHING)
        );
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(
            new TaskStep("search",  "寻找火炮炮架", StepType.INTERACT, List.of()),
            new TaskStep("open",    "打开炮闩",     StepType.INTERACT, List.of()),
            new TaskStep("load",    "装填弹药",     StepType.INTERACT, List.of()),
            new TaskStep("close",   "合上炮闩",     StepType.INTERACT, List.of())
        );
    }

    @Override
    public boolean isTargetBlock(ServerLevel world, BlockPos pos, BlockState state, EntityMaid maid) {
        if ("mount".equals(pipelineData(maid).getString(KEY_NAV_MODE))) {
            return CannonLoadService.isCannonMount(world, pos, state);
        }
        return false;
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");
    }

    @Override
    protected void onEnter(State state, ServerLevel world, EntityMaid maid) {
        pipelineData(maid).putString(KEY_NAV_MODE, state == State.SEARCHING ? "mount" : "none");
    }

    @Override
    protected void cleanup(EntityMaid maid) {
        super.cleanup(maid);
        maid.getPersistentData().remove(KEY_STEP);
        maid.getPersistentData().remove(KEY_MOUNT);
        maid.getPersistentData().remove(KEY_CLEAR_STEP);
        NavigationMemory.clearAllNav(maid);
    }

    // ── 主 tick (每帧目光看向炮架) ──

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        BlockPos mount = loadMountPos(maid);
        if (mount != null) {
            maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(mount));
        }
        return switch (s) {
            case SEARCHING -> tickSearching(world, maid);
            case MOVING    -> tickMoving(world, maid);
            case OPENING   -> tickOpening(world, maid);
            case CLEARING  -> tickClearing(world, maid);
            case LOADING   -> tickLoading(world, maid);
            case CLOSING   -> tickClosing(world, maid);
        };
    }

    // ── 各状态 ──

    private static final int RELOAD_DELAY = 20;

    private State tickSearching(ServerLevel world, EntityMaid maid) {
        CompoundTag data = pipelineData(maid);
        long lastClose = data.getLong("last_close_tick");
        long cdMount = data.getLong(KEY_CD_MOUNT);
        long lastClear = data.getLong(KEY_LAST_CLEAR);
        long now = world.getGameTime();

        List<BlockPos> mounts = CannonLoadService.findAllCannonMounts(world, maid.blockPosition());
        if (mounts.isEmpty()) return null;

        // 第一遍: 跳过loaded + CD中的炮架
        for (BlockPos mount : mounts) {
            if (isLoaded(maid, mount)) continue;
            if (cdMount != 0 && lastClose > 0
                && mount.asLong() == cdMount
                && now - lastClose < RELOAD_DELAY) continue;
            saveMountPos(maid, mount);
            navigateToMount(maid, mount);
            return State.MOVING;
        }

        // 全部loaded/CD → 定期清空loaded标记重扫(检测已发射的炮)
        if (now - lastClear > CLEAR_ALL_INTERVAL) {
            clearAllLoaded(maid);
            data.putLong(KEY_LAST_CLEAR, now);
            // 重试第一个非CD炮架
            for (BlockPos mount : mounts) {
                if (cdMount != 0 && lastClose > 0
                    && mount.asLong() == cdMount
                    && now - lastClose < RELOAD_DELAY) continue;
                saveMountPos(maid, mount);
                navigateToMount(maid, mount);
                return State.MOVING;
            }
        }
        return null;
    }

    private State tickMoving(ServerLevel world, EntityMaid maid) {
        BlockPos mount = loadMountPos(maid);
        if (mount == null) return State.SEARCHING;
        if (!CannonLoadService.isValidMount(world, mount)) return State.SEARCHING;

        if (arrivedAtMount(maid, mount)) return State.OPENING;
        navigateToMount(maid, mount);
        return null;
    }

    private State tickOpening(ServerLevel world, EntityMaid maid) {
        var breechInfo = resolveBreech(world, maid);
        var entity = resolveEntity(world, maid);
        if (breechInfo == null || entity == null) return State.SEARCHING;

        BlockPos mount = loadMountPos(maid);
        var st = CannonLoadService.scanCannon(entity, breechInfo);

        // 炮管已满 → mark loaded
        if (st.hasProjectile() && st.propellantCount() >= 1) {
            markLoaded(maid, mount);
            pipelineData(maid).putLong("last_close_tick", world.getGameTime());
            pipelineData(maid).putLong(KEY_CD_MOUNT, mount.asLong());
            return State.SEARCHING;
        }

        // 炮管有弹药但顺序不对 → 用worm清膛
        if (st.totalCharges() > 0) {
            var slots = CannonLoadService.scanDetailed(entity, breechInfo);
            if (!CannonLoadService.isLoadOrderCorrect(slots)) {
                boolean hasWorm = CannonLoadService.isWorm(maid.getOffhandItem());
                if (hasWorm) {
                    pipelineData(maid).putInt(KEY_CLEAR_STEP, 0);
                    return State.CLEARING;
                }
                unmarkLoaded(maid, mount);
                return State.SEARCHING;
            }
        }

        // 炮管已空(正常) → 开闩装填
        if (!st.hasProjectile() && !st.hasPropellant()) {
            unmarkLoaded(maid, mount);
        }

        if (CannonLoadService.isBreechOpen(breechInfo)) {
            pipelineData(maid).putInt(KEY_STEP, 0);
            return State.LOADING;
        }
        if (CannonLoadService.isOnCooldown(breechInfo)) return null;

        CannonLoadService.toggleBreech(entity, breechInfo);
        return null;
    }

    private State tickClearing(ServerLevel world, EntityMaid maid) {
        var breechInfo = resolveBreech(world, maid);
        var entity = resolveEntity(world, maid);
        if (breechInfo == null || entity == null) return State.SEARCHING;

        if (!CannonLoadService.isBreechOpen(breechInfo)) {
            if (CannonLoadService.isOnCooldown(breechInfo)) return null;
            CannonLoadService.toggleBreech(entity, breechInfo);
            return null;
        }

        CompoundTag data = pipelineData(maid);
        int step = data.getInt(KEY_CLEAR_STEP);

        if (step == 0) {
            data.putInt(KEY_CLEAR_STEP, 1);
            return null;
        }

        CannonLoadService.wormClear(entity, breechInfo, maid);
        return State.SEARCHING;
    }

    private State tickLoading(ServerLevel world, EntityMaid maid) {
        var breechInfo = resolveBreech(world, maid);
        if (breechInfo == null) return State.SEARCHING;
        if (!CannonLoadService.isBreechOpen(breechInfo)) return State.CLOSING;

        PitchOrientedContraptionEntity entity = resolveEntity(world, maid);
        if (entity == null) return State.SEARCHING;

        CompoundTag data = pipelineData(maid);
        int step = data.getInt(KEY_STEP);

        if (world.getGameTime() % 10 == 0) maid.swing(InteractionHand.MAIN_HAND);

        boolean hasRamRod = CannonLoadService.isRamRod(maid.getMainHandItem());

        boolean advanced = switch (step) {
            case 0 -> {
                if (CannonLoadService.isProjectileAtBreech(entity, breechInfo)) { yield true; }
                yield tryLoadOne(world, maid, entity, breechInfo, CannonLoadService::isProjectile);
            }
            case 1 -> {
                boolean loaded = tryLoadOne(world, maid, entity, breechInfo, CannonLoadService::isPropellant);
                if (loaded) { CannonLoadService.ramPush(entity, breechInfo); }
                yield loaded;
            }
            case 2 -> {
                if (!hasRamRod) { yield true; }
                if (CannonLoadService.hasBigCartridgeInTube(entity, breechInfo)) { yield true; }
                boolean loaded = tryLoadOne(world, maid, entity, breechInfo, CannonLoadService::isPropellant);
                if (loaded) { CannonLoadService.ramPush(entity, breechInfo); }
                yield loaded;
            }
            default -> true;
        };

        var st = CannonLoadService.scanCannon(entity, breechInfo);
        LittleMaidMoreAction.LOGGER.info("[LMA/CBC] step={} advanced={} p={} pp={} total={} canLoad={}",
            step, advanced, st.projectileCount(), st.propellantCount(), st.totalCharges(), st.canLoadAtBreech());

        if (advanced) {
            step++;
            data.putInt(KEY_STEP, step);
            if (step >= MAX_SUB) return State.CLOSING;
        }
        return null;
    }

    private State tickClosing(ServerLevel world, EntityMaid maid) {
        var breechInfo = resolveBreech(world, maid);
        var entity = resolveEntity(world, maid);
        if (breechInfo == null || entity == null) return State.SEARCHING;
        if (!CannonLoadService.isBreechOpen(breechInfo) && !CannonLoadService.isOnCooldown(breechInfo)) {
            BlockPos mount = loadMountPos(maid);
            markLoaded(maid, mount);
            CompoundTag pd = pipelineData(maid);
            pd.putLong("last_close_tick", world.getGameTime());
            pd.putLong(KEY_CD_MOUNT, mount.asLong());
            cleanup(maid);
            return State.SEARCHING;
        }
        if (CannonLoadService.isOnCooldown(breechInfo)) return null;

        CannonLoadService.toggleBreech(entity, breechInfo);
        return null;
    }

    // ── 已装填标记 ──

    private static void markLoaded(EntityMaid maid, BlockPos mount) {
        if (mount == null) return;
        long[] cur = maid.getPersistentData().getLongArray(KEY_LOADED);
        long v = mount.asLong();
        for (long l : cur) if (l == v) return;
        long[] next = Arrays.copyOf(cur, cur.length + 1);
        next[cur.length] = v;
        maid.getPersistentData().putLongArray(KEY_LOADED, next);
    }

    private static void unmarkLoaded(EntityMaid maid, BlockPos mount) {
        if (mount == null) return;
        long[] cur = maid.getPersistentData().getLongArray(KEY_LOADED);
        long v = mount.asLong();
        int keep = 0;
        for (long l : cur) if (l != v) keep++;
        if (keep == cur.length) return;
        long[] next = new long[keep];
        int j = 0;
        for (long l : cur) if (l != v) next[j++] = l;
        maid.getPersistentData().putLongArray(KEY_LOADED, next);
    }

    private static boolean isLoaded(EntityMaid maid, BlockPos mount) {
        if (mount == null) return false;
        long[] cur = maid.getPersistentData().getLongArray(KEY_LOADED);
        long v = mount.asLong();
        for (long l : cur) if (l == v) return true;
        return false;
    }

    private static void clearAllLoaded(EntityMaid maid) {
        maid.getPersistentData().putLongArray(KEY_LOADED, new long[0]);
    }

    // ── 辅助 ──

    private CannonLoadService.BreechInfo resolveBreech(ServerLevel world, EntityMaid maid) {
        BlockPos mount = loadMountPos(maid);
        if (mount == null) return null;
        PitchOrientedContraptionEntity entity = CannonLoadService.getContraption(world, mount);
        if (entity == null) return null;
        return CannonLoadService.findBreechInContraption(entity);
    }

    private PitchOrientedContraptionEntity resolveEntity(ServerLevel world, EntityMaid maid) {
        BlockPos mount = loadMountPos(maid);
        if (mount == null) return null;
        return CannonLoadService.getContraption(world, mount);
    }

    private static boolean tryLoadOne(ServerLevel world, EntityMaid maid,
                                       PitchOrientedContraptionEntity entity,
                                       CannonLoadService.BreechInfo breechInfo,
                                       java.util.function.Predicate<ItemStack> filter) {
        IItemHandler inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (filter.test(stack)) {
                ItemStack toLoad = inv.extractItem(i, 1, false);
                if (toLoad.isEmpty()) continue;
                boolean ok = CannonLoadService.loadMunition(entity, breechInfo, toLoad);
                if (!ok) {
                    IItemHandler bp = maid.getAvailableBackpackInv();
                    for (int j = 0; j < bp.getSlots(); j++) {
                        ItemStack remainder = bp.insertItem(j, toLoad, false);
                        if (remainder.isEmpty()) break;
                    }
                }
                return ok;
            }
        }
        return false;
    }

    private void saveMountPos(EntityMaid maid, BlockPos pos) {
        pipelineData(maid).putLong(KEY_MOUNT, pos.asLong());
    }

    private BlockPos loadMountPos(EntityMaid maid) {
        CompoundTag data = pipelineData(maid);
        if (!data.contains(KEY_MOUNT)) return null;
        return BlockPos.of(data.getLong(KEY_MOUNT));
    }

    private void navigateToMount(EntityMaid maid, BlockPos mount) {
        BlockPos stand = CannonLoadService.getStandPos((ServerLevel) maid.level(), mount);
        if (stand == null) return;
        NavigationMemory.setNavTarget(maid, stand);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, stand, 1.0F, 1);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(mount));
    }

    private static boolean arrivedAtMount(EntityMaid maid, BlockPos mount) {
        return mount.distToCenterSqr(maid.position()) < 16.0;
    }
}
