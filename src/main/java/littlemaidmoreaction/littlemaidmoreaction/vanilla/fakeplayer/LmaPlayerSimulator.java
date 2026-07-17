package littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆玩家模拟器 — 模拟左右键操作。
 *
 * <p>四种模式:
 * <ul>
 *   <li>{@code RIGHT_CLICK_ONCE} — 一次性右键交互 (开箱子/放方块/使用物品)</li>
 *   <li>{@code RIGHT_CLICK_CONTINUOUS} — 持续右键交互 (每 tick 重复)</li>
 *   <li>{@code LEFT_CLICK_ONCE} — 一次性左键 (立即破坏方块)</li>
 *   <li>{@code LEFT_CLICK_CONTINUOUS} — 持续左键挖掘 (每 tick 累积 progress)</li>
 * </ul>
 *
 * <p>参考 Create DeployerHandler 模式, 适配 Forge 1.20.1 API。</p>
 */
public final class LmaPlayerSimulator {

    public enum Mode {
        RIGHT_CLICK_ONCE,
        RIGHT_CLICK_CONTINUOUS,
        LEFT_CLICK_ONCE,
        LEFT_CLICK_CONTINUOUS
    }

    /** 射线追踪起点偏移 (模拟玩家眼睛高度) */
    private static final double EYE_OFFSET = 1.0;

    private LmaPlayerSimulator() {}

    // ── 主入口 ──

    /**
     * 执行一次模拟操作。
     *
     * @return true 如果操作消费了交互 (成功)
     */
    public static boolean simulate(LmaFakePlayer fakePlayer, ServerLevel world, BlockPos targetPos,
                                    Direction face, Mode mode) {
        return switch (mode) {
            case RIGHT_CLICK_ONCE, RIGHT_CLICK_CONTINUOUS ->
                simulateRightClick(fakePlayer, world, targetPos, face);
            case LEFT_CLICK_ONCE ->
                simulateLeftClickOnce(fakePlayer, world, targetPos);
            case LEFT_CLICK_CONTINUOUS ->
                simulateLeftClickContinuous(fakePlayer, world, targetPos);
        };
    }

    // ── 右键 ──

    private static boolean simulateRightClick(LmaFakePlayer player, ServerLevel world,
                                               BlockPos targetPos, Direction face) {
        BlockHitResult hit = buildHitResult(targetPos, face);
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);

        // 1. 实体交互优先
        List<Entity> entities = world.getEntitiesOfClass(Entity.class,
            new AABB(targetPos), e -> !(e instanceof LmaFakePlayer));
        if (!entities.isEmpty()) {
            Entity entity = entities.get(world.random.nextInt(entities.size()));
            List<ItemEntity> capturedDrops = new ArrayList<>();
            entity.captureDrops(capturedDrops);

            if (entity.interact(player, hand).consumesAction()) {
                entity.captureDrops(null);
                capturedDrops.forEach(e -> placeInMaidInv(player, e.getItem()));
                return true;
            }
            if (entity instanceof LivingEntity le
                && stack.interactLivingEntity(player, le, hand).consumesAction()) {
                entity.captureDrops(null);
                capturedDrops.forEach(e -> placeInMaidInv(player, e.getItem()));
                return true;
            }
            entity.captureDrops(null);
        }

        // 2. 方块交互事件
        BlockState state = world.getBlockState(targetPos);
        PlayerInteractEvent.RightClickBlock event =
            new PlayerInteractEvent.RightClickBlock(player, hand, targetPos, hit);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled() || event.getUseBlock() == Event.Result.DENY) {
            return false;
        }

        // 3. 物品优先 onItemUseFirst
        UseOnContext ctx = new UseOnContext(player, hand, hit);
        if (!stack.isEmpty() && stack.onItemUseFirst(ctx) != InteractionResult.PASS) {
            return true;
        }

        // 4. 方块交互 use()
        if (event.getUseBlock() != Event.Result.DENY
            && state.use(world, player, hand, hit).consumesAction()) {
            return true;
        }

        // 5. 物品右键 useOn + use
        if (!stack.isEmpty() && event.getUseItem() != Event.Result.DENY) {
            if (stack.useOn(ctx).consumesAction()) return true;
            var result = stack.use(world, player, hand);
            if (result.getResult().consumesAction()) {
                player.setItemInHand(hand, result.getObject());
                return true;
            }
        }

        return false;
    }

    // ── 左键一次性 (立即破坏) ──

    private static boolean simulateLeftClickOnce(LmaFakePlayer player, ServerLevel world, BlockPos targetPos) {
        BlockState state = world.getBlockState(targetPos);
        if (state.isAir()) return false;

        ItemStack tool = player.getMainHandItem();

        // 检查工具能否挖掘
        if (!tool.isEmpty() && !tool.isCorrectToolForDrops(state)) {
            return false; // 工具不对, 不挖
        }

        // 火事件
        Direction face = Direction.UP;
        PlayerInteractEvent.LeftClickBlock event =
            new PlayerInteractEvent.LeftClickBlock(player, targetPos, face);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return false;

        // 检查能否挖掘
        if (!state.canHarvestBlock(world, targetPos, player)) {
            return false; // 工具等级不够
        }

        // 直接破坏 + 收集掉落 (tryHarvestBlock 内部已扣工具耐久)
        tryHarvestBlock(player, world, targetPos, state);

        return true;
    }

    // ── 左键持续 (累积挖掘进度) ──

    private static boolean simulateLeftClickContinuous(LmaFakePlayer player, ServerLevel world, BlockPos targetPos) {
        BlockState state = world.getBlockState(targetPos);
        if (state.isAir()) {
            player.clearBreakingProgress(world);
            return true; // 方块已被破坏 (可能被其他东西破坏)
        }

        if (!world.mayInteract(player, targetPos)) return false;
        if (state.getShape(world, targetPos).isEmpty()) return false;

        ItemStack tool = player.getMainHandItem();
        if (!tool.isEmpty() && !tool.isCorrectToolForDrops(state)) {
            player.clearBreakingProgress(world);
            return false;
        }

        // 火事件 (仅第一次)
        Direction face = Direction.UP;
        PlayerInteractEvent.LeftClickBlock event =
            new PlayerInteractEvent.LeftClickBlock(player, targetPos, face);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return false;

        // 攻击方块
        if (event.getUseBlock() != Event.Result.DENY) {
            state.attack(world, targetPos, player);
        }

        // 累进破坏进度
        float destroyProgress = state.getDestroyProgress(player, world, targetPos) * 16f;
        if (destroyProgress <= 0) {
            player.clearBreakingProgress(world);
            return false;
        }

        player.addBreakingProgress(destroyProgress);
        world.destroyBlockProgress(player.getId(), targetPos, (int) (player.getBreakingProgress() * 10));

        // 进度 >= 1 → 完成破坏 (tryHarvestBlock 内部已扣工具耐久)
        if (player.getBreakingProgress() >= 1.0f) {
            player.clearBreakingProgress(world);
            tryHarvestBlock(player, world, targetPos, state);
        }

        return true;
    }

    // ── 方块破坏 + 掉落收集 ──

    /**
     * 模拟 ServerPlayerGameMode#tryHarvestBlock — 破坏方块并收集掉落物到女仆背包。
     *
     * <p>参考 Create DeployerHandler.tryHarvestBlock() 模式, 适配 Forge 1.20.1。</p>
     */
    private static void tryHarvestBlock(LmaFakePlayer player, ServerLevel world, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = world.getBlockEntity(pos);

        // 1. Fire block break event
        net.minecraftforge.event.level.BlockEvent.BreakEvent breakEvent =
            new net.minecraftforge.event.level.BlockEvent.BreakEvent(world, pos, state, player);
        MinecraftForge.EVENT_BUS.post(breakEvent);
        if (breakEvent.isCanceled()) return;

        // 2. 扣除工具耐久
        ItemStack held = player.getMainHandItem().copy();
        ItemStack prevHeld = player.getMainHandItem();
        if (!prevHeld.isEmpty()) {
            prevHeld.mineBlock(world, state, pos, player);
        }

        // 3. 移除方块
        boolean canHarvest = state.canHarvestBlock(world, pos, player);
        boolean removed = state.onDestroyedByPlayer(world, pos, player, canHarvest, world.getFluidState(pos));
        if (removed) {
            state.getBlock().destroy(world, pos, state);
        }

        // 4. 收集掉落 (放入女仆背包)
        if (canHarvest) {
            List<ItemStack> drops = Block.getDrops(state, (ServerLevel) world, pos, blockEntity, player, held);
            for (ItemStack drop : drops) {
                placeInMaidInv(player, drop);
            }
            state.spawnAfterBreak((ServerLevel) world, pos, held, true);
        }
    }

    // ── 工具方法 ──

    /** 构建射线命中结果 */
    private static BlockHitResult buildHitResult(BlockPos pos, Direction face) {
        Vec3 hitVec = new Vec3(
            pos.getX() + 0.5 + face.getStepX() * 0.5,
            pos.getY() + 0.5 + face.getStepY() * 0.5,
            pos.getZ() + 0.5 + face.getStepZ() * 0.5
        );
        return new BlockHitResult(hitVec, face, pos, false);
    }

    /** 将物品放入女仆背包 */
    private static void placeInMaidInv(LmaFakePlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        var inv = player.getMaid().getAvailableInv(false);
        ItemStack remainder = ItemHandlerHelper.insertItem(inv, stack, false);
        if (!remainder.isEmpty()) {
            // 背包满了 → 掉落在地
            player.getMaid().spawnAtLocation(remainder);
        }
    }

    // ── 女仆数据同步 ──

    /** 将假玩家手持物品同步回女仆主手 */
    public static void syncHandToMaid(LmaFakePlayer player) {
        player.getMaid().setItemInHand(InteractionHand.MAIN_HAND,
            player.getItemInHand(InteractionHand.MAIN_HAND));
    }

    /** 清理: 女仆卸载时调用 */
    public static void cleanup(LmaFakePlayer player, ServerLevel world) {
        player.clearBreakingProgress(world);
    }
}
