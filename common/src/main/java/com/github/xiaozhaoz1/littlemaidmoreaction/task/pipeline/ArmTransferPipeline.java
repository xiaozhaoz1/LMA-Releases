package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
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
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>FSM 写法 (v79.61x 收敛): tick 用 switch 分派到每状态顶层方法 (长状态拆方法,
 * 短状态内联) — 无处理器表/上下文双重间接; 能力 API 组合
 * (NbtCodecs/NavigationUtil/ContainerOutput)。
 */
public final class ArmTransferPipeline extends TaskStateMachine<ArmTransferPipeline.State> implements TaskConfigurable {

    enum State { TO_TAKE, TAKING, TO_DEPOSIT, DEPOSITING }

    // 值指向 TaskKeys 唯一源 (v79.55 收编 — 原独立字面量同键双定义漂移风险, lma_prev_task 先例同类)
    public static final String KEY_TAKE = TaskKeys.ARM_TAKE;
    public static final String KEY_DEPOSIT = TaskKeys.ARM_DEPOSIT;
    static final String KEY_ITEM = TaskKeys.ARM_ITEM;

    // ── 引擎必需 ──

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.TO_TAKE; }
    @Override public String taskType() { return "arm_transfer"; }
    /** 固定工作点任务 (工作站交互) — TLM 骑乘中不脱离坐骑 */
    @Override public boolean workPointTask() { return true; }

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

    /** 搬运黑白名单配置 GUI (per-maid) */
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
        // KEY_TAKE/KEY_DEPOSIT 持久保留 — 玩家只设一次，重启/TLM重置后仍在
        d.remove(KEY_ITEM);
        com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory.clearAllNav(maid);
    }

    // ── 状态机驱动 (switch 分派 — 每状态一个顶层方法, 新增状态 = 加枚举 + 加一行分派) ──

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return switch (s) {
            case TO_TAKE -> handleToTake(maid, world);
            case TAKING -> handleTaking(maid, world);
            case TO_DEPOSIT -> handleToDeposit(maid, world);
            case DEPOSITING -> handleDepositing(maid, world);
        };
    }

    // ── 状态处理器 (每状态一个方法 — 单职责, 组合能力 API) ──

    /** TO_TAKE — 导航到取货点 */
    private static State handleToTake(EntityMaid maid, ServerLevel world) {
        BlockPos takePos = readPos(maid.getPersistentData(), KEY_TAKE);
        if (takePos == null) return null;
        if (arrived(maid, takePos)) return State.TAKING;
        navigateTo(maid, takePos);
        return null;
    }

    /** TAKING — 取货 (黑白名单 → 读源 → 计算 → 提取 → 记录物品) */
    private static State handleTaking(EntityMaid maid, ServerLevel world) {
        CompoundTag data = maid.getPersistentData();
        BlockPos takePos = readPos(data, KEY_TAKE);
        if (takePos == null) return null;
        // 搬运黑白名单 (per-maid 覆盖全局)
        var cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.TaskConfigs.get(maid, "arm_transfer");
        var lists = ItemFilters.effectivePair(cfg,
                ActiveTaskConfig.ARM_BLACKLIST.get(), ActiveTaskConfig.ARM_WHITELIST.get());
        var item = ArmTransferService.readSourceItem(maid, takePos,
                stack -> ItemFilters.isAllowed(stack, lists.get(0), lists.get(1)));
        if (item.isEmpty()) { return null; } // 空源 → 等待
        int count = ArmTransferService.computeExtractCount(maid, item);
        if (count <= 0) { return null; }
        // 溢出退还算法统一到 ContainerOutput
        var handler = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                .getHandler(world, takePos);
        // v79.48 修复 #10.3: 容器消失 (方块被拆) → 终态处理 — 防无限囤货死循环
        if (handler == null) {
            com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi
                    .showFail(maid, "取货容器已消失");
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.fail(maid, "取货容器已消失");
            return null;
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                .withdrawItemStack(maid, handler, item, count);
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.MaidSwing.onInterval(maid, 20);
        data.putString(KEY_ITEM, ArmTransferService.itemId(maid, item));
        return State.TO_DEPOSIT;
    }

    /** TO_DEPOSIT — 导航到放货点 */
    private static State handleToDeposit(EntityMaid maid, ServerLevel world) {
        BlockPos depositPos = readPos(maid.getPersistentData(), KEY_DEPOSIT);
        if (depositPos == null) return null;
        if (arrived(maid, depositPos)) return State.DEPOSITING;
        navigateTo(maid, depositPos);
        return null;
    }

    /** DEPOSITING — 放货 (读物品 → 计算 → 存入 → 回取货) */
    private static State handleDepositing(EntityMaid maid, ServerLevel world) {
        CompoundTag data = maid.getPersistentData();
        BlockPos depositPos = readPos(data, KEY_DEPOSIT);
        if (depositPos == null) return null;
        String itemId = data.getString(KEY_ITEM);
        var mItem = itemId.isEmpty()
            ? ArmTransferService.readMaidItem(maid)
            : ArmTransferService.findMaidItem(maid, itemId);
        if (mItem.isEmpty()) { return State.TO_TAKE; } // 无物品 → 回去取
        int count = ArmTransferService.computeDepositCount(maid, depositPos, mItem);
        if (count <= 0) { return null; } // 目标满 → 等待
        var handler = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                .getHandler(world, depositPos);
        // v79.48 修复 #10.3 对称 (审计 H2): 放货容器消失 → 终态 — 防 DEPOSITING 永久卡死
        if (handler == null) {
            com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi
                    .showFail(maid, "放货容器已消失");
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.fail(maid, "放货容器已消失");
            return null;
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.container.ContainerOutput
                .depositItemStack(maid, handler, mItem, count);
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.MaidSwing.onInterval(maid, 20);
        return State.TO_TAKE;
    }

    // ── 辅助 ──

    /** 坐标读取 — 样板收敛 (api/nbt/NbtCodecs, 双平台格式兼容) */
    private static BlockPos readPos(CompoundTag data, String key) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(data, key);
    }

    /** 导航三件套 — 样板收敛 (api/navigation/NavigationUtil) */
    private static void navigateTo(EntityMaid maid, BlockPos target) {
        com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil.navigateTo(maid, target);
    }

    private static boolean arrived(EntityMaid m, BlockPos p) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil.arrived(m, p);
    }

}
