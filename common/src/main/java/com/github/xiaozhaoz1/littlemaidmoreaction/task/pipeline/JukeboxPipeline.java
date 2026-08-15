package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.DataKey;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.JukeboxService;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.ProgressNotifier;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 唱片机管道 (Phase 2) — 扫描唱片 → 写规则 → 导航+弹出入碟。
 * v79.45: 工作站基类 — GMPM 驱动 (execute 恒 CONTINUE → 永续任务, max=0 分支不触发)。
 * v79.61x execute 瘦身样本 2: 原 JukeboxExecute 相位机 (INSERTING→PLAYING→EJECTING→PICKUP_WAIT)
 * 收编进管线 — 每相位一个顶层方法 (switch 分派), 单拍业务动作 = {@link JukeboxService} (插碟/弹碟)。
 * v79.61x S1: 相位机迁移进 {@link TaskStateMachine} (方案 A) — 原 int 状态键
 * DataKey.JUKEBOX_PHASE (lma_jukebox_phase) 退役, 状态入 FSM 内存态 (lma_pl_jukebox.fsm);
 * 相位时间戳保留 DataKey.JUKEBOX_TICK (lma_jukebox_tick, root 键) — onEnter 归一时点。
 */
public final class JukeboxPipeline extends TaskStateMachine<JukeboxPipeline.Phase> implements TaskConfigurable {

    /** 播放相位 (原 JukeboxExecute.Phase 收编) — 状态随 FSM 内存态跨拍持久 */
    enum Phase { INSERTING, PLAYING, EJECTING, PICKUP_WAIT }

    @Override protected Class<Phase> stateClass() { return Phase.class; }
    @Override protected Phase initialState() { return Phase.INSERTING; }
    @Override public String taskType() { return "jukebox"; }
    /** 固定工作点任务 (工作站交互) — TLM 骑乘中不脱离坐骑 */
    @Override public boolean workPointTask() { return true; }
    /** 工作站式门 (到达 + 节拍 30t) — 对齐原 WorkStationPipeline 语义 */
    @Override protected boolean workStationGated() { return true; }
    @Override public int executeInterval() { return 30; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.minecraft.world.level.block.Blocks.JUKEBOX); }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("play", "播放唱片", StepType.INTERACT, List.of())); }

    /** 唱片黑白名单配置 GUI (per-maid) */
    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "jukebox");
    }

    /** 纯验证 — 仅扫描背包是否有唱片(读操作)，不写日志/通知 */
    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        // 唱片黑白名单 (per-maid 覆盖全局)
        var cfg = pipelineConfig(maid);
        var lists = ItemFilters.effectivePair(cfg,
                ActiveTaskConfig.JUKEBOX_BLACKLIST.get(), ActiveTaskConfig.JUKEBOX_WHITELIST.get());
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
//? if 1.20.1 {
            if (!s.isEmpty() && s.is(ItemTags.MUSIC_DISCS) && ItemFilters.isAllowed(s, lists.get(0), lists.get(1))) {
//?} else {
            if (!s.isEmpty() && s.is(ItemTags.CREEPER_DROP_MUSIC_DISCS) && ItemFilters.isAllowed(s, lists.get(0), lists.get(1))) {
//?}
                if (target.isEmpty()) return PipelineResult.ok("");
                if (s.getDescriptionId().contains(target) || s.getItem().toString().contains(target))
                    return PipelineResult.ok("");
            }
        }
        return PipelineResult.failed(ProgressNotifier.NO_DISC);
    }

    /** 机内碟为空约束 (双平台差异收敛) */
    private static boolean jukeboxEmpty(JukeboxBlockEntity jukebox) {
//? if 1.20.1 {
        return jukebox.getFirstItem().isEmpty();
//?} else {
        return jukebox.getItem(0).isEmpty();
//?}
    }

    /** 计时到期判定 — 相位时间戳 (DataKey.JUKEBOX_TICK) 与当前 tick 差 */
    private static boolean elapsed(EntityMaid maid, long now, long interval) {
        long phaseTick = MaidData.get(maid, DataKey.JUKEBOX_TICK);
        // anti-stale — 异常时间戳 (时钟回绕/跨 session) 视为刚到, 归零重计时
        if (phaseTick > now || phaseTick == 0) {
            MaidData.put(maid, DataKey.JUKEBOX_TICK, now);
            phaseTick = now;
        }
        return Math.abs(now - phaseTick) >= interval;
    }

    /** 相位进入 — 归一时点 (原每处 putInt(JUKEBOX_TICK, now) 汇聚) */
    @Override
    protected void onEnter(Phase state, ServerLevel world, EntityMaid maid) {
        MaidData.put(maid, DataKey.JUKEBOX_TICK, world.getGameTime());
        LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} -> {}", maid.getId(), state);
    }

    /** 相位机 tick — 每节拍派发一次 (门控已保证到达+节拍), 恒续 (不计数不完成) */
    @Override
    protected Phase tick(Phase phase, ServerLevel world, EntityMaid maid) {
        BlockPos pos = maid.getBrain().getMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get())
                .get().currentBlockPosition();
        if (!(world.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox)) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} pos is not a jukebox", maid.getId());
            return null;
        }
        long now = world.getGameTime();
        return switch (phase) {
            case INSERTING -> handleInserting(world, maid, pos, jukebox, now);
            case PLAYING -> handlePlaying(maid, jukebox, now);
            case EJECTING -> handleEjecting(maid, jukebox);
            case PICKUP_WAIT -> handlePickupWait(maid, now);
        };
    }

    // ── 相位处理器 (原 JukeboxExecute switch 各臂, 每相位一个顶层方法) ──

    /** INSERTING — 选碟 (黑白名单 + 目标匹配/随机) → 插碟 → PLAYING */
    private Phase handleInserting(ServerLevel world, EntityMaid maid, BlockPos pos,
                                  JukeboxBlockEntity jukebox, long now) {
        if (!jukeboxEmpty(jukebox)) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: has disc, skip to PLAYING", maid.getId());
            return Phase.PLAYING;
        }
        // 生效黑白名单 (per-maid lma_cfg_jukebox 覆盖全局) — 管线内直调 pipelineConfig (原经 TaskRegistry cast)
        CompoundTag cfg = pipelineConfig(maid);
        var lists = ItemFilters.effectivePair(cfg,
                ActiveTaskConfig.JUKEBOX_BLACKLIST.get(), ActiveTaskConfig.JUKEBOX_WHITELIST.get());
        IItemHandler inv = maid.getAvailableInv(true);
        List<ItemStack> discs = new ArrayList<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
//? if 1.20.1 {
            if (s.is(ItemTags.MUSIC_DISCS) && ItemFilters.isAllowed(s, lists.get(0), lists.get(1))) discs.add(s);
//?} else {
            if (s.is(ItemTags.CREEPER_DROP_MUSIC_DISCS) && ItemFilters.isAllowed(s, lists.get(0), lists.get(1))) discs.add(s);
//?}
        }
        if (discs.isEmpty()) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: no discs in inventory", maid.getId());
            return null;
        }
        String target = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData.getTarget(maid);
        ItemStack chosen;
        if (!target.isEmpty()) {
            chosen = null;
            for (ItemStack d : discs) {
                String id = d.getItem().toString().toLowerCase();
                String name = d.getDisplayName().getString().toLowerCase();
                if (id.contains(target.toLowerCase()) || name.contains(target.toLowerCase())) {
                    chosen = d;
                    break;
                }
            }
            if (chosen == null) {
                LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} disc '{}' not found", maid.getId(), target);
                return null;
            }
        } else {
            chosen = discs.get(ThreadLocalRandom.current().nextInt(discs.size()));
        }
//? if 1.20.1 {
        var discKey = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(chosen.getItem());
//?} else {
        var discKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(chosen.getItem());
//?}
        LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: chose {}/{} from {} discs",
            maid.getId(), discKey, chosen.getDisplayName().getString(), discs.size());
        IItemHandler maidInv = maid.getAvailableInv(true);
        boolean inserted = false;
        for (int i = 0; i < maidInv.getSlots(); i++) {
            if (ItemStackHelper.isSameItem(maidInv.getStackInSlot(i), chosen)) {
                ItemStack extracted = maidInv.extractItem(i, 1, false);
                if (!extracted.isEmpty()) {
                    JukeboxService.insertDisc(jukebox, extracted, world, pos);
                    inserted = true;
                    break;
                }
            }
        }
        if (inserted) {
            MaidChatBubbleApi.showInfo(maid, "正在播放: " + chosen.getHoverName().getString());
            int wait = ActiveTaskConfig.JUKEBOX_WAIT_TICKS.get();
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING→PLAYING (wait {} ticks={}min)",
                maid.getId(), wait, wait / 20 / 60);
            return Phase.PLAYING;
        }
        return null;
    }

    /** PLAYING — 播放中, 空 → INSERTING; 到时 → EJECTING */
    private Phase handlePlaying(EntityMaid maid, JukeboxBlockEntity jukebox, long now) {
        if (jukeboxEmpty(jukebox)) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING: empty, back to INSERTING", maid.getId());
            return Phase.INSERTING;
        }
        if (elapsed(maid, now, ActiveTaskConfig.JUKEBOX_WAIT_TICKS.get())) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING→EJECTING", maid.getId());
            return Phase.EJECTING;
        }
        return null;
    }

    /** EJECTING — 弹出 → PICKUP_WAIT; 空 → INSERTING */
    private Phase handleEjecting(EntityMaid maid, JukeboxBlockEntity jukebox) {
        boolean ejected = JukeboxService.ejectDisc(jukebox, maid);
        if (ejected) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} EJECTING: disc ejected", maid.getId());
            return Phase.PICKUP_WAIT;
        }
        LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} EJECTING: empty, back to INSERTING", maid.getId());
        return Phase.INSERTING;
    }

    /** PICKUP_WAIT — 拾取等待计时, 到时 → INSERTING */
    private Phase handlePickupWait(EntityMaid maid, long now) {
        if (elapsed(maid, now, VanillaConstants.JUKEBOX_PICKUP_TICKS)) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PICKUP_WAIT→INSERTING", maid.getId());
            return Phase.INSERTING;
        }
        return null;
    }
}
