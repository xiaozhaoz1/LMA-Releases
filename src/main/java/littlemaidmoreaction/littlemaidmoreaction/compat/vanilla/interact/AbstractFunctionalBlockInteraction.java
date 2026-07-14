package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaConstants;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.core.memory.LmaTaskMemory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 功能方块互动抽象模板 (v12.3 — 导航+交互一体化)。
 *
 * <p>参考 TLM {@code MaidCollectHoneyTask} 的"走到目标→到达→交互"模式。
 * 使用 PersistentData 跨 tick 保存导航目标，每 tick 自动：
 * <ul>
 *   <li>无目标时：BlockSearch 找最近方块 → 保存位置 → 设置 brain WALK_TARGET</li>
 *   <li>走路中：刷新 WALK_TARGET（防止被 brain 清除）</li>
 *   <li>到达时（距离&lt;3格）：执行 doInteract → 清除导航数据</li>
 *   <li>超时检测：30秒仍未到达 → 清除目标（下次重新搜索）</li>
 * </ul>
 *
 * <p>子类不变 — 只需覆写 {@link #id()}, {@link #displayName()},
 * {@link #validActions()}, {@link #doInteract(BlockPos, BlockState, EntityMaid, Map, String)}。</p>
 *
 * <h3>模板方法执行流 (v12.3)</h3>
 * <pre>
 * execute(RuleContext, Map)
 *   → canBrainMoving()?  不 → return (坐着/骑乘/睡觉/拴绳)
 *   → 有保存的导航目标?
 *     → 目标方块仍有效?  不 → 清除, 重新搜索
 *     → 已到达(距离&lt;3)?
 *       是 → clearNavData → clear WALK_TARGET → doInteract → return
 *       否 → 刷新 WALK_TARGET → return (继续走)
 *   → 无目标 → BlockSearch.findBlocks → 保存目标 → set WALK_TARGET → return
 * </pre>
 *
 * @see AbstractBlockInteraction
 * @see com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCollectHoneyTask
 */
public abstract class AbstractFunctionalBlockInteraction extends AbstractBlockInteraction {

    // ★ v12.7 P0: 导航数据已迁移到 Brain Memory (LmaTaskMemory)

    /** 到达判定距离平方 (3格 = 9) */
    private static final double ARRIVE_DIST_SQR = VanillaConstants.ARRIVE_DIST_SQR;
    /** 导航超时 tick (30秒) */
    private static final int NAV_TIMEOUT = VanillaConstants.NAV_TIMEOUT_TICKS;

    /** 子类定义的有效操作列表，用于 GUI 下拉选择 + 运行时验证 */
    protected abstract List<String> validActions();

    /** ★ v12.6: 子类覆写默认目标方块 (如 furnace→furnace, brewing→brewing_stand) */
    protected String defaultBlockId() { return "minecraft:crafting_table"; }

    @Override
    public ActionCategory category() { return ActionCategory.WORLD; }

    @Override
    public List<TypedParam<?>> params() {
        List<String> actions = validActions();
        List<TypedParam<?>> all = new ArrayList<>();
        all.addAll(super.params());
        if (actions.size() > 1) {
            all.add(new TypedParam.SelectParam("action", "操作类型", actions.get(0), actions));
        }
        all.add(new TypedParam.StringParam("item_id", "物品ID(可选)", ""));
        return List.copyOf(all);
    }

    // ─── 导航 + 交互一体化入口 ───

    /**
     * 入口：导航状态机 — 找到方块→走过去→到达→交互。
     *
     * <p>每次规则触发都会调用此方法。根据当前导航状态决定：
     * <ul>
     *   <li>搜索新目标并开始走路</li>
     *   <li>继续走路（刷新路径）</li>
     *   <li>到达后执行交互</li>
     * </ul>
     */
    @Override
    public void execute(RuleContext ctx, Map<String, String> rawParams) {
        // 验证 action
        List<String> valid = validActions();
        if (!valid.isEmpty()) {
            String action = rawParams.getOrDefault("action", valid.get(0));
            if (!valid.contains(action)) {
                rawParams.put("action", valid.get(0));
            }
        }

        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return;

        // 女仆忙（坐着/骑乘/睡觉/拴绳）→ 不能走路
        if (!maid.canBrainMoving()) return;

        String action = rawParams.getOrDefault("action",
            valid.isEmpty() ? "" : valid.get(0));

        // ── 第一阶段：检查 Brain Memory 中的导航目标 ──
        BlockPos targetPos = LmaTaskMemory.getNavTarget(maid);
        if (targetPos != null) {
            long savedTick = LmaTaskMemory.getNavStartTick(maid);
            long now = maid.level().getGameTime();

            if (now - savedTick > NAV_TIMEOUT || savedTick > now) {
                LmaTaskMemory.clearAllNav(maid);
            } else if (isBlockStillValid(maid.level(), targetPos)) {
                double distSqr = targetPos.distSqr(maid.blockPosition());

                if (distSqr < ARRIVE_DIST_SQR) {
                    LmaTaskMemory.clearAllNav(maid);
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    BlockState state = maid.level().getBlockState(targetPos);
                    doInteract(targetPos, state, maid, rawParams, action);
                    return;
                }

                BehaviorUtils.setWalkAndLookTargetMemories(maid, targetPos, 1.0F, 2);
                return;
            } else {
                LmaTaskMemory.clearAllNav(maid);
            }
        }

        // ── 第二阶段：搜索新目标 ──
        String blockId = rawParams.getOrDefault("block_id", defaultBlockId());
        int range = parseInt(rawParams.get("range"), 16);
        int vertical = parseInt(rawParams.get("vertical"), 4);

        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl == null) return;
        Block targetBlock = ForgeRegistries.BLOCKS.getValue(rl);
        if (targetBlock == null) return;

        List<BlockSearch.Match> matches = BlockSearch.findBlocks(
            maid.level(), maid.blockPosition(), range, vertical,
            (pos, state) -> state.is(targetBlock) && matchBlock(pos, state, maid)
        );

        if (matches.isEmpty()) return;

        BlockPos nearest = matches.get(0).pos();

        // 如果已经在目标旁边 → 直接交互，不需要走路
        if (nearest.distSqr(maid.blockPosition()) < ARRIVE_DIST_SQR) {
            doInteract(nearest, matches.get(0).state(), maid, rawParams, action);
            return;
        }

        // ★ v12.7 P0: 保存到 Brain Memory
        LmaTaskMemory.setNavTarget(maid, nearest);
        LmaTaskMemory.setNavStartTick(maid, maid.level().getGameTime());
        BehaviorUtils.setWalkAndLookTargetMemories(maid, nearest, 1.0F, 2);
    }

    /**
     * 桥接方法 (final)：从 params 中提取 action 字符串，传递给 doInteract。
     * 子类不应覆写此方法 — 覆写 {@link #doInteract} 代替。
     *
     * <p>注意：v12.3 后此方法仅由 execute() 的导航完成分支调用，
     * 或由父类 AbstractBlockInteraction.execute() 的旧路径调用。</p>
     */
    @Override
    protected final void interact(BlockPos pos, BlockState state, EntityMaid maid,
                                   Map<String, String> params) {
        String action = params.getOrDefault("action",
            validActions().isEmpty() ? "" : validActions().get(0));
        doInteract(pos, state, maid, params, action);
    }

    /**
     * 子类实现此方法完成具体的方块交互逻辑。
     *
     * @param pos    目标方块坐标（女仆已到达 3 格内）
     * @param state  方块状态
     * @param maid   女仆实体
     * @param params 原始参数 (含 action, block_id, item_id 等)
     * @param action 已校验的操作类型
     */
    protected abstract void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                                        Map<String, String> params, String action);

    /**
     * ★ v12.6: 公开入口 — 供 LmaFlowCoordinationBehavior 直接调用。
     * @return true=执行了有效操作, false=无事可做(如熔炉无产物可取)
     */
    public final boolean doInteractDirect(BlockPos pos, BlockState state, EntityMaid maid,
                                           Map<String, String> params, String action) {
        return doInteractWithResult(pos, state, maid, params, action);
    }

    /**
     * 子类覆写此方法返回操作是否有效。
     * 默认调用 doInteract 并返回 true。
     */
    protected boolean doInteractWithResult(BlockPos pos, BlockState state, EntityMaid maid,
                                            Map<String, String> params, String action) {
        doInteract(pos, state, maid, params, action);
        return true;
    }

    // ─── 导航工具 (v12.7 P0: 迁移到 Brain Memory) ───

    /** ★ v12.7: 简化检查 — 方块非空气即可 (Brain Memory 不存 blockId) */
    private boolean isBlockStillValid(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        return !level.getBlockState(pos).isAir();
    }

    // ─── BlockEntity 工具 ───

    @SuppressWarnings("unchecked")
    protected <T extends BlockEntity> Optional<T> getBlockEntity(Level level, BlockPos pos, Class<T> type) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && type.isInstance(be)) {
            return Optional.of((T) be);
        }
        return Optional.empty();
    }

    protected Optional<BlockEntity> getBlockEntity(Level level, BlockPos pos) {
        return Optional.ofNullable(level.getBlockEntity(pos));
    }

    // ─── 物品栏工具 ───

    protected Optional<IItemHandler> getItemHandler(BlockEntity be) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
    }

    protected IItemHandler getMaidInventory(EntityMaid maid) {
        return maid.getAvailableInv(true);
    }

    // ─── 物品转移工具 ───

    protected int transferMaidToBlock(EntityMaid maid, IItemHandler blockInv,
                                       int targetSlot, Predicate<ItemStack> filter, int amount) {
        IItemHandler maidInv = getMaidInventory(maid);
        int transferred = 0;

        for (int i = 0; i < maidInv.getSlots() && transferred < amount; i++) {
            ItemStack stack = maidInv.getStackInSlot(i);
            if (stack.isEmpty() || !filter.test(stack)) continue;

            int toTake = Math.min(amount - transferred, stack.getCount());
            ItemStack extracted = maidInv.extractItem(i, toTake, false);
            if (extracted.isEmpty()) continue;

            ItemStack existing = blockInv.getStackInSlot(targetSlot);
            if (!existing.isEmpty() && !ItemHandlerHelper.canItemStacksStack(existing, extracted)) {
                ItemStack remainder = ItemHandlerHelper.insertItem(blockInv, extracted, false);
                if (!remainder.isEmpty()) {
                    maidInv.insertItem(i, remainder, false);
                }
                transferred += toTake - remainder.getCount();
            } else {
                ItemStack remainder = blockInv.insertItem(targetSlot, extracted, false);
                if (!remainder.isEmpty()) {
                    maidInv.insertItem(i, remainder, false);
                }
                transferred += toTake - remainder.getCount();
            }
        }
        return transferred;
    }

    protected boolean transferBlockToMaid(EntityMaid maid, IItemHandler blockInv,
                                           int slot, int amount) {
        IItemHandler maidInv = getMaidInventory(maid);
        ItemStack extracted = blockInv.extractItem(slot, amount, false);
        if (extracted.isEmpty()) return false;

        ItemStack remainder = ItemHandlerHelper.insertItem(maidInv, extracted, false);
        if (!remainder.isEmpty()) {
            blockInv.insertItem(slot, remainder, false);
        }
        return remainder.getCount() < extracted.getCount();
    }

    // ─── 物品查找工具 ───

    protected Optional<ItemStack> findInMaidInv(EntityMaid maid, Predicate<ItemStack> filter) {
        IItemHandler inv = getMaidInventory(maid);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    protected ItemStack extractFromMaidInv(EntityMaid maid, Predicate<ItemStack> filter, int amount) {
        IItemHandler inv = getMaidInventory(maid);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return inv.extractItem(i, Math.min(amount, stack.getCount()), false);
            }
        }
        return ItemStack.EMPTY;
    }

    // ─── 参数解析工具 ───

    protected Optional<Item> parseItemId(Map<String, String> params) {
        String itemId = params.getOrDefault("item_id", "");
        if (itemId.isEmpty()) return Optional.empty();
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return Optional.empty();
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return Optional.ofNullable(item);
    }

    // ─── ItemStack 工具 ───

    protected boolean isSameItem(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    // ─── 流程任务完成标记 ───

    /**
     * 交互成功后标记流程任务完成。
     *
     * <p>写入 lma_flow_state="completed" 并清除缓存，
     * TaskEngine 会在下个 tick 检测到 → counter++ → 决定循环或停止。
     *
     * <p>子类在 doInteract() 成功后调用此方法，无需在规则中额外添加 set_flow_task。
     *
     * @param maid 女仆实体
     */
    protected void completeFlowTask(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        String task = data.getString("lma_flow_task");
        if (!task.isEmpty()) {
            data.putString("lma_flow_state", "completed");
            data.remove("lma_flow_cached");
            data.putLong("lma_flow_tick", maid.level().getGameTime());
            RuleEngine.handleEvent("task_changed",
                new littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext(maid, null, null));
        }
    }

    // ─── 视觉/音效反馈 ───

    /**
     * 播放交互反馈：挥臂动画 + 方块音效。
     *
     * <p>参考 TLM {@code MaidCollectHoneyTask}。
     *
     * @param maid  女仆
     * @param pos   方块位置（音效来源）
     * @param sound 音效（支持 SoundEvent 直接传，Holder 类型用 .value() 提取）
     */
    protected void playInteractionFeedback(EntityMaid maid, BlockPos pos, SoundEvent sound) {
        maid.swing(InteractionHand.MAIN_HAND);
        maid.level().playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // ─── 通用工具 ───

    protected static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    // ─── ★ v13: WirelessIO 隙间箱子搜索 ───

    /**
     * 从女仆背包 + 隙间箱子中提取物品。
     * 优先搜索女仆背包，未找到时搜索隙间连接的箱子。
     */
    public static ItemStack extractFromMaidAndWirelessChest(EntityMaid maid,
                                                                Predicate<ItemStack> filter, int amount) {
        // 1. 先查女仆背包
        IItemHandler maidInv = maid.getAvailableInv(true);
        for (int i = 0; i < maidInv.getSlots(); i++) {
            ItemStack stack = maidInv.getStackInSlot(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return maidInv.extractItem(i, Math.min(amount, stack.getCount()), false);
            }
        }
        // 2. 查隙间箱子
        IItemHandler chestInv = getWirelessChestHandler(maid);
        if (chestInv != null) {
            for (int i = 0; i < chestInv.getSlots(); i++) {
                ItemStack stack = chestInv.getStackInSlot(i);
                if (!stack.isEmpty() && filter.test(stack)) {
                    return chestInv.extractItem(i, Math.min(amount, stack.getCount()), false);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 检查女仆背包+隙间箱子是否有匹配物品 */
    public static boolean hasInMaidOrWirelessChest(EntityMaid maid, Predicate<ItemStack> filter) {
        IItemHandler maidInv = maid.getAvailableInv(true);
        for (int i = 0; i < maidInv.getSlots(); i++) {
            if (!maidInv.getStackInSlot(i).isEmpty() && filter.test(maidInv.getStackInSlot(i))) return true;
        }
        IItemHandler chestInv = getWirelessChestHandler(maid);
        if (chestInv != null) {
            for (int i = 0; i < chestInv.getSlots(); i++) {
                if (!chestInv.getStackInSlot(i).isEmpty() && filter.test(chestInv.getStackInSlot(i))) return true;
            }
        }
        return false;
    }

    /** ★ v15: 公开包装 — 供 AI 工具查询隙间箱子 */
    @javax.annotation.Nullable
    public static IItemHandler getWirelessChestHandlerPublic(EntityMaid maid) {
        return getWirelessChestHandler(maid);
    }

    /** 获取隙间饰品连接的箱子 IItemHandler */
    @javax.annotation.Nullable
    private static IItemHandler getWirelessChestHandler(EntityMaid maid) {
        var baubleInv = maid.getMaidBauble();
        for (int i = 0; i < baubleInv.getSlots(); i++) {
            ItemStack bauble = baubleInv.getStackInSlot(i);
            if (bauble.isEmpty()) continue;
            BlockPos bindingPos = ItemWirelessIO.getBindingPos(bauble);
            if (bindingPos == null) continue;
            // 检查距离
            float maxDist = maid.getRestrictRadius();
            if (maid.distanceToSqr(bindingPos.getX(), bindingPos.getY(), bindingPos.getZ()) > maxDist * maxDist) continue;
            var be = maid.level().getBlockEntity(bindingPos);
            if (be == null) continue;
            var handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve();
            if (handler.isPresent()) return handler.get();
        }
        return null;
    }
}
