package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.io.IExecutor;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ArmTransferService;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 女仆搬运管线 (v46 迁移至 TaskStateMachine, v53 移出 compat/create).
 *
 * <p>四状态循环:
 * <pre>
 * TO_TAKE → TAKING → TO_DEPOSIT → DEPOSITING → TO_TAKE → ...
 * </pre>
 *
 * <p>TO_TAKE/TO_DEPOSIT: 导航阶段。到达后进入对应执行状态。
 * <br>TAKING/DEPOSITING: 执行阶段。成功后推进，空源/目标满时原地等待。
 *
 * <p>v79.7 示范重构 (状态机写法): 状态行为 = 处理器表 (Map&lt;State, Handler&gt;, 每状态
 * 独立方法 — 替代巨型 switch) + 类型化状态上下文 ({@link StateCtx}) + 能力 API 组合
 * (NbtCodecs/NavigationUtil/ContainerOutput — v79.5 样板吸收)。行为零变化。
 */
public final class ArmTransferPipeline extends TaskStateMachine<ArmTransferPipeline.State> {

    enum State { TO_TAKE, TAKING, TO_DEPOSIT, DEPOSITING }

    public static final String KEY_TAKE = "lma_arm_take";
    public static final String KEY_DEPOSIT = "lma_arm_deposit";
    static final String KEY_ITEM = "lma_arm_item";

    // ── v79.7 示范: 状态处理器表 — 每状态一个独立方法 (新增状态 = 加枚举 + 加一行表) ──

    /** 状态 → 处理器 (返回 null = 停留当前状态; 返回 State = 推进) */
    private static final Map<State, Function<StateCtx, State>> HANDLERS = Map.of(
            State.TO_TAKE,    ArmTransferPipeline::handleToTake,
            State.TAKING,     ArmTransferPipeline::handleTaking,
            State.TO_DEPOSIT, ArmTransferPipeline::handleToDeposit,
            State.DEPOSITING, ArmTransferPipeline::handleDepositing);

    /**
     * 状态上下文 — 类型化传递 (替代 tick 内分散读 PD/坐标)。
     * maid/level 由引擎注入; data = 女仆 PersistentData (读侧统一入口, 写仍走键)。
     */
    private record StateCtx(EntityMaid maid, ServerLevel world, CompoundTag data) {}

    // ── 引擎必需 ──

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.TO_TAKE; }
    @Override public String taskType() { return "arm_transfer"; }
    @Override public boolean needsGameTick() { return true; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.TO_TAKE,     Set.of(State.TAKING),
            State.TAKING,      Set.of(State.TO_DEPOSIT),
            State.TO_DEPOSIT,  Set.of(State.DEPOSITING),
            State.DEPOSITING,  Set.of(State.TO_TAKE)
        );
    }

    // ── 可选覆写 ──

    @Override
    public List<TaskStep> steps() {
        return List.of(new TaskStep("move", "搬运物品", StepType.INTERACT, List.of()));
    }

    /** v67.3: 搬运黑白名单配置 GUI (per-maid) */
    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "arm_transfer");
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        var d = maid.getPersistentData();
        if (!d.contains(KEY_TAKE) || !d.contains(KEY_DEPOSIT))
            return PipelineResult.failed("坐标未设置");
        return PipelineResult.ok("就绪");
    }

    @Override
    protected void onEnter(State state, ServerLevel world, EntityMaid maid) {
        if (state == State.TO_TAKE || state == State.TO_DEPOSIT) {
            com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory.clearAllNav(maid);
        }
    }

    @Override
    protected void cleanup(EntityMaid maid) {
        super.cleanup(maid);
        var d = maid.getPersistentData();
        // v52: KEY_TAKE/KEY_DEPOSIT 持久保留 — 玩家只设一次，重启/TLM重置后仍在
        d.remove(KEY_ITEM);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory.clearAllNav(maid);
    }

    /**
     * 覆写 executor — 坐标未设置时跳过 tick (保持原行为)。
     */
    @Override
    public IExecutor executor() {
        return new IExecutor() {
            @Override
            public TaskResult execute(ServerLevel world, EntityMaid maid, BlockPos pos, CompoundTag data) {
                if (!data.contains(KEY_TAKE) || !data.contains(KEY_DEPOSIT))
                    return TaskResult.CONTINUE;
                tick(world, maid);
                return TaskResult.CONTINUE;
            }
        };
    }

    // ── 状态机驱动 (v79.7: 表驱动 — 引擎仍调 tick(State), 分发到处理器) ──

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return HANDLERS.get(s).apply(new StateCtx(maid, world, maid.getPersistentData()));
    }

    // ── 状态处理器 (每状态一个方法 — 单职责, 组合能力 API) ──

    /** TO_TAKE — 导航到取货点 */
    private static State handleToTake(StateCtx ctx) {
        BlockPos takePos = readPos(ctx.data(), KEY_TAKE);
        if (takePos == null) return null;
        if (arrived(ctx.maid(), takePos)) return State.TAKING;
        navigateTo(ctx.maid(), takePos);
        return null;
    }

    /** TAKING — 取货 (黑白名单 → 读源 → 计算 → 提取 → 记录物品) */
    private static State handleTaking(StateCtx ctx) {
        BlockPos takePos = readPos(ctx.data(), KEY_TAKE);
        if (takePos == null) return null;
        // v67.3: 搬运黑白名单 (per-maid 覆盖全局)
        var cfg = pipelineConfigOf(ctx.maid());
        var black = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_BLACKLIST),
                ActiveTaskConfig.ARM_BLACKLIST.get());
        var white = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_WHITELIST),
                ActiveTaskConfig.ARM_WHITELIST.get());
        var item = ArmTransferService.readSourceItem(ctx.maid(), takePos,
                stack -> ItemFilters.isAllowed(stack, black, white));
        if (item.isEmpty()) { return null; } // 空源 → 等待
        int count = ArmTransferService.computeExtractCount(ctx.maid(), item);
        if (count <= 0) { return null; }
        // v79.5: 溢出退还算法统一到 ContainerOutput
        var handler = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                .getHandler(ctx.world(), takePos);
        if (handler != null) {
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                    .withdrawItemStack(ctx.maid(), handler, item, count);
        }
        if (ctx.world().getGameTime() % 20 == 0) ctx.maid().swing(InteractionHand.MAIN_HAND);
        ctx.data().putString(KEY_ITEM, itemId(ctx.maid(), item));
        return State.TO_DEPOSIT;
    }

    /** TO_DEPOSIT — 导航到放货点 */
    private static State handleToDeposit(StateCtx ctx) {
        BlockPos depositPos = readPos(ctx.data(), KEY_DEPOSIT);
        if (depositPos == null) return null;
        if (arrived(ctx.maid(), depositPos)) return State.DEPOSITING;
        navigateTo(ctx.maid(), depositPos);
        return null;
    }

    /** DEPOSITING — 放货 (读物品 → 计算 → 存入 → 回取货) */
    private static State handleDepositing(StateCtx ctx) {
        BlockPos depositPos = readPos(ctx.data(), KEY_DEPOSIT);
        if (depositPos == null) return null;
        String itemId = ctx.data().getString(KEY_ITEM);
        var mItem = itemId.isEmpty()
            ? ArmTransferService.readMaidItem(ctx.maid())
            : findMaidItem(ctx.maid(), itemId);
        if (mItem.isEmpty()) { return State.TO_TAKE; } // 无物品 → 回去取
        int count = ArmTransferService.computeDepositCount(ctx.maid(), depositPos, mItem);
        if (count <= 0) { return null; } // 目标满 → 等待
        var handler = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                .getHandler(ctx.world(), depositPos);
        if (handler != null) {
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                    .depositItemStack(ctx.maid(), handler, mItem, count);
        }
        if (ctx.world().getGameTime() % 20 == 0) ctx.maid().swing(InteractionHand.MAIN_HAND);
        return State.TO_TAKE;
    }

    // ── 辅助 ──

    /** 坐标读取 — v79.5 样板收敛 (api/nbt/NbtCodecs, 双平台格式兼容) */
    private static BlockPos readPos(CompoundTag data, String key) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(data, key);
    }

    /** 导航三件套 — v79.5 样板收敛 (api/navigation/NavigationUtil) */
    private static void navigateTo(EntityMaid maid, BlockPos target) {
        com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil.navigateTo(maid, target);
    }

    private static boolean arrived(EntityMaid m, BlockPos p) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil.arrived(m, p);
    }

    /** 管线配置读取 (实例方法依赖 — 状态处理器为静态, 经 maid 取当前管线) */
    private static CompoundTag pipelineConfigOf(EntityMaid maid) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry
                .get(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid))
                .pipeline().pipelineConfig(maid);
    }

    /** 物品 registry id (双平台) — 记录搬运物品供放货匹配 */
    private static String itemId(EntityMaid maid, ItemStack item) {
        ResourceLocation id;
//? if 1.20.1 {
        id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
//?} else {
        id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem());
//?}
        return id != null ? id.toString() : item.getDescriptionId();
    }

    private static ItemStack findMaidItem(EntityMaid maid, String itemId) {
        var rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return ItemStack.EMPTY;
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            ResourceLocation id;
//? if 1.20.1 {
            id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
//?} else {
            id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
//?}
            if (rl.equals(id)) return s.copy();
        }
        return ItemStack.EMPTY;
    }
}
