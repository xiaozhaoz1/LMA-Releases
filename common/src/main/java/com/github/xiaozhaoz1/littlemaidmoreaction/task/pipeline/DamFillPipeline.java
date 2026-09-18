package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v79.62.2 填坝排水任务 (dam_fill) — 大海排水前置: 围着目标区域 (size×size 区块) 外圈筑重力方块墙,
 * 可选排水 (开关 drain_enabled).
 *
 * <p><b>阶段 1 筑墙 (BUILD_WALL)</b> — 四方向优化 (用户裁定):
 * <ul>
 *   <li>墙 = 4 个方向: 北 (z=minZ-1) / 南 (z=maxZ+1) / 西 (x=minX-1) / 东 (x=maxX+1),
 *       每方向一条线逐列处理</li>
 *   <li>女仆 TP 到列所在区块中心 (加载) → 检测该列「堆顶是否到 topY」(落定完整)
 *       → 未到 → 放铁砧/沙填该列空位 (自然下落) → 等 20 tick (1 秒) → 再检测 → 补缺口 → 直到完整</li>
 *   <li>完整 → 下一列 → 该方向完 → 下一方向</li>
 * </ul>
 *
 * <p><b>阶段 2 排水 (DRAIN, 开关 drain_enabled 默认开)</b> — 按排从墙边往里 (用户裁定):
 * <ul>
 *   <li>第 1 排 = 靠墙那排 (x=minX, z minZ→maxZ 横穿所有区块) → 清完 → x+1 往里 → 直到 x>maxX</li>
 *   <li>每格液体 → 空气 + 破坏粒子 + 主手挥桶 (空桶不变) + 桶舀水声</li>
 *   <li>女仆 TP 到当前排所在区块中心; 主手空桶 (无桶卡住提醒)</li>
 * </ul>
 *
 * <p><b>HOME 模式</b>: 任务期间强制 home (防 TLM 拉回, 女仆正确传送); 结束恢复 (PD wasHome).
 * <b>水下呼吸</b>: 任务期间持续给 WATER_BREATHING 效果 (防溺亡).
 *
 * <p>cfg (lma_cfg_dam_fill): start / size / input / drain_enabled. 游标/高度全 PD 内存 (用户裁定).
 */
public final class DamFillPipeline extends TaskStateMachine<DamFillPipeline.Phase> implements TaskConfigurable {

    /** 状态 */
    public enum Phase { BUILD_WALL, DRAIN }

    /** cfg 键 */
    /** v79.63.15: 单女仆区域区块数 (pipelineConfig; 缺省读全局 DAM_FILL_DEFAULT_CHUNKS ✓) */
    public static final String KEY_SIZE = "size";
    public static final String KEY_INPUT = "input";
    public static final String KEY_DRAIN_ENABLED = "drain_enabled";

    /** 重力方块判定 — 沙/红沙/砾石/铁砧/龙蛋/混凝土粉末 (FALLING_BLOCK 会下落) */
    private static boolean isGravityBlock(ItemStack s) {
        net.minecraft.world.item.Item i = s.getItem();
        return i == net.minecraft.world.item.Items.SAND
                || i == net.minecraft.world.item.Items.RED_SAND
                || i == net.minecraft.world.item.Items.GRAVEL
                || i == net.minecraft.world.item.Items.ANVIL
                || i == net.minecraft.world.item.Items.CHIPPED_ANVIL
                || i == net.minecraft.world.item.Items.DAMAGED_ANVIL
                || i == net.minecraft.world.item.Items.DRAGON_EGG
                || i == net.minecraft.world.item.Items.WHITE_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.ORANGE_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.MAGENTA_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.LIGHT_BLUE_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.YELLOW_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.LIME_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.PINK_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.GRAY_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.LIGHT_GRAY_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.CYAN_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.PURPLE_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.BLUE_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.BROWN_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.GREEN_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.RED_CONCRETE_POWDER
                || i == net.minecraft.world.item.Items.BLACK_CONCRETE_POWDER;
    }

    /** 每 tick 操作预算 (防卡) */
    private static final int OP_BUDGET = 8;
    /** 列间等待 (tick) — 重力方块下落落定, 用户裁定 1 秒 = 20 tick */
    private static final int COLUMN_WAIT_TICKS = 20;
    /** 水下呼吸续期间隔 (tick) — 效果 300t, 每 200t 续防断 */
    private static final int BREATH_INTERVAL = 200;

    @Override protected Class<Phase> stateClass() { return Phase.class; }
    @Override protected Phase initialState() { return Phase.BUILD_WALL; }
    @Override public String taskType() { return "dam_fill"; }
    @Override public boolean isLongRunning() { return true; }

    /** v79.62.2 两模式独立 (用户裁定): 填坝=只筑墙(墙完完成); 排水=只排水(排完完成).
     *  模式 = cfg drain_enabled: true=排水模式(initial DRAIN), false=填坝模式(initial BUILD_WALL).
     *  initialState 被引擎首次 tick 调用, 但模式从 cfg 读 — 覆写 tick 在首次按模式分派. */
    @Override
    protected Map<Phase, Set<Phase>> transitions() {
        return Map.of();   // 独立模式, 无状态转换
    }

    @Override
    public List<TaskStep> steps() {
    // ⚠ 改相位/状态时必须同步本步骤声明 — steps 是**用户可见的粗粒度语义**, 与内部状态枚举**不同层**;
    //    二者无自动校验 (6 态→4 步这类多对一是正常的), 详见错题 #291。
            
        return List.of(
                new TaskStep("wall", "筑重力方块墙", StepType.INTERACT, List.of()),
                new TaskStep("drain", "排空墙内水", StepType.INTERACT, List.of()));
    }

    /** 当前模式: true=排水(只排水), false=填坝(只筑墙, 默认) — cfg drain_enabled */
    private static boolean drainMode(CompoundTag cfg) {
        return cfg.contains(KEY_DRAIN_ENABLED) && cfg.getBoolean(KEY_DRAIN_ENABLED);
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        CompoundTag cfg = pipelineConfig(maid);
        if (!cfg.contains(TaskKeys.CFG_START) || !cfg.contains(KEY_INPUT)) {
            return PipelineResult.failed("未标记起点/输入箱 (木棍标记起点+输入箱)");
        }
        return PipelineResult.ok("");
    }

    /** 配置屏 (TLM 任务设置标签页): 排水开关 (drain_enabled) */
    @Override
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory.createMenuProvider(maid,
            net.minecraft.network.chat.Component.literal("dam_fill"),
            (cid, inv, maidId) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigMenu(cid, inv, maidId));
    }

    /** PD 键 (内存态, 不落 NBT — 用户裁定: cfg 只存 start/size/input/drain_enabled) */
    private static final String PD_TOP_Y = "topY";
    private static final String PD_DIR = "dir";          // 方向 0北 1南 2西 3东
    private static final String PD_COL = "col";          // 方向内列索引
    private static final String PD_COL_WAIT = "colWait";
    private static final String PD_ROW_X = "rowX";       // 排水当前排 x
    private static final String PD_ROW_Z = "rowZ";
    private static final String PD_WAS_HOME = "wasHome";

    @Override
    protected Phase tick(Phase state, ServerLevel world, EntityMaid maid) {
        CompoundTag cfg = pipelineConfig(maid);   // cfg 只存标记配置
        CompoundTag pd = MaidData.pl(maid, "dam_fill");   // 游标/高度 = 内存 PD (零 NBT 写)
        // 任务期间: HOME 模式 + 水下呼吸 (持续维持)
        ensureHomeMode(maid, pd);
        ensureWaterBreathing(world, maid);
        // v79.62.2 两模式独立: 模式从 cfg 读, 首次 tick 按模式跳到对应状态 (引擎 initialState 固定 BUILD_WALL)
        boolean drain = drainMode(cfg);
        if (state == Phase.BUILD_WALL && drain) {
            // 排水模式 → 直接排水 (不筑墙)
            return tickDrain(world, maid, cfg, pd) ? null : null;
        }
        if (state == Phase.DRAIN && !drain) {
            // 填坝模式 → 筑墙
            return tickBuildWall(world, maid, cfg, pd) ? null : null;
        }
        return switch (state) {
            case BUILD_WALL -> tickBuildWall(world, maid, cfg, pd) ? null : null;   // 墙完 → 任务完成 (complete 在方法内)
            case DRAIN -> tickDrain(world, maid, cfg, pd) ? null : null;            // 排完 → 任务完成 (complete 在方法内)
        };
    }

    // ── 任务期间维持: HOME + 水下呼吸 ──

    /** 强制 HOME 模式 (防 TLM 拉回, 女仆正确传送) — 首次记录 wasHome, 结束恢复 */
    private static void ensureHomeMode(EntityMaid maid, CompoundTag pd) {
        if (!pd.contains(PD_WAS_HOME)) {
            pd.putBoolean(PD_WAS_HOME, maid.isHomeModeEnable());
            if (!maid.isHomeModeEnable()) maid.setHomeModeEnable(true);
        }
    }

    /** 水下呼吸效果 (任务期间持续, 防溺亡) */
    private static void ensureWaterBreathing(ServerLevel world, EntityMaid maid) {
        if (world.getGameTime() % BREATH_INTERVAL == 0) {
            maid.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 300, 0, false, false));
        }
    }

    // ── 阶段 1: 筑墙 (四方向) ──

    /**
     * @return true = 外圈墙全部筑完 (→ 阶段 2 或完成)
     */
    private boolean tickBuildWall(ServerLevel world, EntityMaid maid, CompoundTag cfg, CompoundTag pd) {
        BlockPos start = NbtCodecs.readBlockPos(cfg, TaskKeys.CFG_START);
        int size = cfg.contains(KEY_SIZE) ? cfg.getInt(KEY_SIZE) : com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS.get();   // v79.63.15: 全局默认 (原写死 1 ✗)
        if (start == null) { TaskDispatcher.fail(maid, "无起点"); return false; }
        int scx = start.getX() >> 4, scz = start.getZ() >> 4;
        int minCX = scx - size / 2, maxCX = minCX + size - 1;
        int minCZ = scz - size / 2, maxCZ = minCZ + size - 1;
        int minX = minCX * 16, maxX = maxCX * 16 + 15;
        int minZ = minCZ * 16, maxZ = maxCZ * 16 + 15;
        int topY = pd.contains(PD_TOP_Y) ? pd.getInt(PD_TOP_Y) : start.getY() + 3;
        pd.putInt(PD_TOP_Y, topY);

        // v79.62.2 用户裁定修正: 窗口制(10位置) 反复补到「该位置最高层 topY 实心」才算完整;
        // 窗口内全部完整才推进窗口; 方向内全部窗口完成(col>=len)才换方向; 四面全完才进排水.
        int dir = pd.getInt(PD_DIR);
        int col = pd.contains(PD_COL) ? pd.getInt(PD_COL) : 0;
        int colWait = pd.contains(PD_COL_WAIT) ? pd.getInt(PD_COL_WAIT) : 0;
        if (colWait > 0) {
            colWait--;
            pd.putInt(PD_COL_WAIT, colWait);
            return false;
        }

        int len = (maxX - minX) + 3;   // 边长度 (含角)
        int placedAny = 0;
        int windowEnd = Math.min(col + 10, len);   // 窗口不越方向末尾
        while (col < windowEnd) {
            BlockPos colPos = wallColumnPos(dir, col, minX, maxX, minZ, maxZ);
            pd.putInt(PD_DIR, dir); pd.putInt(PD_COL, col);
            int ccx = colPos.getX() >> 4, ccz = colPos.getZ() >> 4;
            if (!world.hasChunk(ccx, ccz)) {
                VoidExcavationService.teleportToVoid(world, maid, new BlockPos(ccx * 16 + 8, topY, ccz * 16 + 8));
                return false;
            }
            if (columnComplete(world, colPos, topY)) {
                col++;   // 该位置已到顶 → 下一位置
                pd.putInt(PD_COL, col);
                continue;
            }
            // 缺 → topY 放 1 个铁砧 (自然下落)
            ItemStack block = takeGravityBlock(world, maid, cfg);
            if (block == null || block.isEmpty()) {
                if (world.getGameTime() % 200 == 0) {
                    MaidChatBubbleApi.showFail(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.dam_fill.no_input"));
                }
                return false;
            }
            Block b = net.minecraft.world.level.block.Block.byItem(block.getItem());
            world.setBlock(new BlockPos(colPos.getX(), topY, colPos.getZ()), b.defaultBlockState(), 3);
            maid.swing(InteractionHand.MAIN_HAND);
            placedAny++;
            col++;
            pd.putInt(PD_COL, col);
        }
        if (placedAny > 0) {
            // 补了 → 等 1 秒落定 → 回窗口起点 (窗口内可能还没到顶, 继续补)
            pd.putInt(PD_COL, Math.max(0, col - 10));
            pd.putInt(PD_COL_WAIT, COLUMN_WAIT_TICKS);
            return false;
        }
        // placedAny == 0: 窗口内全到顶 → col 已到 windowEnd
        if (col >= len) {
            // 该方向全完 → 下一方向
            dir++;
            col = 0;
            if (dir >= 4) {
                // 四面全完 → 填坝模式任务完成 (v79.62.2 独立模式, 不接排水)
                pd.remove(PD_DIR); pd.remove(PD_COL); pd.remove(PD_COL_WAIT);
                restoreHomeMode(maid, pd);
                MaidChatBubbleApi.showComplete(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.dam_fill.wall_done"));
                TaskDispatcher.complete(maid);
                return true;
            }
            pd.putInt(PD_DIR, dir); pd.putInt(PD_COL, 0);
            return false;
        }
        // 窗口完成 → 下窗口 (col 停在 windowEnd)
        pd.putInt(PD_COL, col);
        return false;
    }

    /** 方向+列 → 世界坐标; null = 该方向扫完.
     *  北 (dir0): z=minZ-1, x 从 minX-1 到 maxX+1; 南 (dir1): z=maxZ+1, x 同;
     *  西 (dir2): x=minX-1, z 从 minZ-1 到 maxZ+1; 东 (dir3): x=maxX+1, z 同. */
    private static BlockPos wallColumnPos(int dir, int col, int minX, int maxX, int minZ, int maxZ) {
        int len = (maxX - minX) + 3;   // 边长度 (含角) = (maxX-minX+1)+2
        switch (dir) {
            case 0: if (col >= len) return null; return new BlockPos(minX - 1 + col, 0, minZ - 1);
            case 1: if (col >= len) return null; return new BlockPos(minX - 1 + col, 0, maxZ + 1);
            case 2: if (col >= len) return null; return new BlockPos(minX - 1, 0, minZ - 1 + col);
            case 3: if (col >= len) return null; return new BlockPos(maxX + 1, 0, minZ - 1 + col);
            default: return null;
        }
    }

    /** 该列是否完整: 堆顶已到 topY (重力方块落定, 墙该列成型) */
    private static boolean columnComplete(ServerLevel world, BlockPos p, int topY) {
        BlockState top = world.getBlockState(new BlockPos(p.getX(), topY, p.getZ()));
        return !top.isAir() && top.getFluidState().isEmpty();
    }

    /** 放一整列重力方块: 从堆顶+1 到 topY 的所有空位一次放上 (预算内) — 铁砧/沙放置后
     *  自然下落堆叠到海底 (连续下落声). 调用方等 1 秒再检测补缺口.
     *  @return true = 放了一块或多块; false = 无方块卡住等补给 */
    private static boolean placeGravityColumn(ServerLevel world, EntityMaid maid, CompoundTag cfg,
                                              BlockPos p, int topY) {
        // ① 找当前堆顶 (从 topY 往下第一个实体块; 无底 → 跳过该列)
        int stackTop = -1;
        for (int y = topY; y >= world.getMinBuildHeight(); y--) {
            BlockState st = world.getBlockState(new BlockPos(p.getX(), y, p.getZ()));
            if (!st.isAir() && st.getFluidState().isEmpty()) { stackTop = y; break; }
        }
        if (stackTop < 0) return true;   // 无底 (虚空) — 跳过该列
        if (stackTop >= topY) return true;   // 已到顶
        // ② 从堆顶+1 到 topY: 一次放满整列 (预算内) — 每块自然下落堆叠 (有声音)
        int placed = 0;
        for (int y = stackTop + 1; y <= topY; y++) {
            ItemStack block = takeGravityBlock(world, maid, cfg);   // cfg 读 input (标记配置)
            if (block == null || block.isEmpty()) return placed > 0;   // 无沙 → 已放的落下, 卡住等补给
            Block b = net.minecraft.world.level.block.Block.byItem(block.getItem());   // 双平台 Item→Block
            world.setBlock(new BlockPos(p.getX(), y, p.getZ()), b.defaultBlockState(), 3);   // 放置后自然下落 (有声音)
            maid.swing(InteractionHand.MAIN_HAND);   // 挥手
            placed++;
            if (placed >= OP_BUDGET) break;   // 预算封顶, 下 tick 继续本列
        }
        return true;
    }

    /** 从输入箱取一个重力方块 */
    private static ItemStack takeGravityBlock(ServerLevel world, EntityMaid maid, CompoundTag cfg) {
        BlockPos input = NbtCodecs.readBlockPos(cfg, KEY_INPUT);
        if (input == null) return ItemStack.EMPTY;
        var handler = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationContainerService
                .getHandler(world, input);
        if (handler == null) return ItemStack.EMPTY;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && isGravityBlock(s)) {
                return handler.extractItem(i, 1, false);
            }
        }
        return ItemStack.EMPTY;
    }

    // ── 阶段 2: 排水 (按排从墙边往里, 一排横穿所有区块) ──

    /**
     * 每 tick 清一排的若干格 (预算内); 一排清完 → 下一排 (x+1 往里) → 直到 x>maxX 完成.
     * 游标/高度全 PD 内存.
     */
    private boolean tickDrain(ServerLevel world, EntityMaid maid, CompoundTag cfg, CompoundTag pd) {
        BlockPos start = NbtCodecs.readBlockPos(cfg, TaskKeys.CFG_START);
        int size = cfg.contains(KEY_SIZE) ? cfg.getInt(KEY_SIZE) : com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS.get();   // v79.63.15: 全局默认 (原写死 1 ✗)
        if (start == null) { TaskDispatcher.fail(maid, "无起点"); return false; }
        int scx = start.getX() >> 4, scz = start.getZ() >> 4;
        int minCX = scx - size / 2, maxCX = minCX + size - 1;
        int minCZ = scz - size / 2, maxCZ = minCZ + size - 1;
        int minX = minCX * 16, maxX = maxCX * 16 + 15;
        int minZ = minCZ * 16, maxZ = maxCZ * 16 + 15;
        int topY = pd.contains(PD_TOP_Y) ? pd.getInt(PD_TOP_Y) : start.getY() + 3;
        pd.putInt(PD_TOP_Y, topY);

        int rowX = pd.contains(PD_ROW_X) ? pd.getInt(PD_ROW_X) : minX;
        int rowZ = pd.contains(PD_ROW_Z) ? pd.getInt(PD_ROW_Z) : minZ;

        // 主手必须有空桶 (排水舀水); 无桶 → 卡住提醒, 不排水
        if (!ensureBucket(world, maid, cfg)) {
            if (world.getGameTime() % 200 == 0) {
                MaidChatBubbleApi.showFail(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.dam_fill.need_bucket"));
            }
            return false;
        }

        int budget = OP_BUDGET;
        while (budget > 0) {
            if (rowX > maxX) {
                // 全部排完 → 完成
                pd.remove(PD_ROW_X); pd.remove(PD_ROW_Z);
                restoreHomeMode(maid, pd);
                MaidChatBubbleApi.showComplete(maid, net.minecraft.network.chat.Component.translatable("bubble.littlemaidmoreaction.dam_fill.done"));
                TaskDispatcher.complete(maid);
                return true;
            }
            // TP 到当前格所在区块中间 (加载)
            int ccx = rowX >> 4, ccz = rowZ >> 4;
            if (!world.hasChunk(ccx, ccz)) {
                VoidExcavationService.teleportToVoid(world, maid, new BlockPos(ccx * 16 + 8, topY, ccz * 16 + 8));
                return false;
            }
            // 清该格液体 (该列 topY 往下所有液体)
            clearCellLiquids(world, maid, new BlockPos(rowX, 0, rowZ), topY);
            budget--;
            // 推进 z (该排横穿所有区块); 到 maxZ → 下一排 (x+1 往里)
            rowZ++;
            if (rowZ > maxZ) {
                rowZ = minZ;
                rowX++;
            }
            pd.putInt(PD_ROW_X, rowX); pd.putInt(PD_ROW_Z, rowZ);
        }
        return false;
    }

    /** 清一列所有液体 (topY 往下到海底): 每格 → 空气 + 粒子 + 挥手 + 舀水声 */
    private static boolean clearCellLiquids(ServerLevel world, EntityMaid maid, BlockPos col, int topY) {
        boolean any = false;
        for (int y = topY; y >= world.getMinBuildHeight(); y--) {
            BlockPos p = new BlockPos(col.getX(), y, col.getZ());
            if (!world.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            BlockState st = world.getBlockState(p);
            if (st.getFluidState().isEmpty()) continue;
            world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            world.levelEvent(2001, p, Block.getId(st));   // 破坏粒子
            world.playSound(null, p, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);   // 舀水声
            world.gameEvent(maid, GameEvent.FLUID_PICKUP, p);
            maid.swing(InteractionHand.MAIN_HAND);   // 挥手
            any = true;
            break;   // 每 tick 一格 (预算外) — 逐格清
        }
        return any;
    }

    /** 主手必须有空桶 (排水舀水; 桶舀水后仍空不变). 主手无桶 → 找背包/输入箱空桶; 都没有 → false. */
    private static boolean ensureBucket(ServerLevel world, EntityMaid maid, CompoundTag cfg) {
        if (maid.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET)) return true;
        // ★ v79.67.1 修「换桶把主手物品换没」(用户实测): 原两处分支都是 `setItemInHand(MAIN_HAND, 桶)`
        //   **直接覆盖** ⇒ 原主手物品既没回背包也没落地 ⇒ **永久丢失** ✗
        //   这是错题 #162「**丢物品族**」的**第三处** (前两处 ChainHarvestExecute / BlockUpCoordinator 已收敛到
        //   `vanilla/output/item/HandSwap`; WorkEat / NearbyCollect 亦各自保全) ⇒ 本次一并收敛, 不再手写换手链 ✓
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(net.minecraft.world.item.Items.BUCKET)) {
                ItemStack bucket = inv.extractItem(i, 1, false);   // 只取 1 个 (桶可堆叠 16 — 保持原语义 ✓)
                if (bucket.isEmpty()) continue;
                ItemStack old = maid.getMainHandItem();
                maid.setItemInHand(InteractionHand.MAIN_HAND, bucket);
                if (!old.isEmpty()) {
                    // 旧物保全三链的"背包 → 落地"段 (HandSwap 原语; 满则落地, 绝不消失) ✓
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.HandSwap.stashOrDrop(maid, old);
                }
                return true;
            }
        }
        BlockPos input = NbtCodecs.readBlockPos(cfg, KEY_INPUT);
        if (input != null) {
            var handler = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationContainerService
                    .getHandler(world, input);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack s = handler.getStackInSlot(i);
                    if (s.is(net.minecraft.world.item.Items.BUCKET)) {
                        ItemStack taken = handler.extractItem(i, 1, false);
                        if (taken.isEmpty()) continue;
                        ItemStack old = maid.getMainHandItem();
                        maid.setItemInHand(InteractionHand.MAIN_HAND, taken);
                        if (!old.isEmpty()) {
                            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.HandSwap.stashOrDrop(maid, old);   // 同上 (原此处亦直接覆盖 ✗)
                        }
                        return true;
                    }
                }
            }
        }
        return false;   // 无桶 → 卡住提醒
    }

    /** 恢复 HOME 模式 (结束/清理时) */
    private static void restoreHomeMode(EntityMaid maid, CompoundTag pd) {
        if (pd.contains(PD_WAS_HOME)) {
            boolean wasHome = pd.getBoolean(PD_WAS_HOME);
            if (!wasHome) maid.setHomeModeEnable(false);
            pd.remove(PD_WAS_HOME);
        }
    }

    // ── 生命周期 ──

    @Override
    protected void cleanup(EntityMaid maid) {
        super.cleanup(maid);
        CompoundTag pd = MaidData.pl(maid, "dam_fill");
        pd.remove(PD_TOP_Y); pd.remove(PD_DIR); pd.remove(PD_COL); pd.remove(PD_COL_WAIT);
        pd.remove(PD_ROW_X); pd.remove(PD_ROW_Z);
        restoreHomeMode(maid, pd);
    }
}
