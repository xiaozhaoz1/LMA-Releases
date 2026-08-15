package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.DataKey;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager;
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
import java.util.concurrent.ThreadLocalRandom;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 唱片机管道 (Phase 2) — 扫描唱片 → 写规则 → 导航+弹出入碟。
 * v79.45: 工作站基类 — GMPM 驱动 (execute 恒 CONTINUE → 永续任务, max=0 分支不触发)。
 * v79.61x execute 瘦身样本 2: 原 JukeboxExecute 相位机 (INSERTING→PLAYING→EJECTING→PICKUP_WAIT)
 * 收编进管线 — 状态键 DataKey.JUKEBOX_PHASE/JUKEBOX_TICK 原样 (行为零变化);
 * 每相位一个顶层方法 (switch 分派), 单拍业务动作 = {@link JukeboxService} (插碟/弹碟); JukeboxExecute 删除。
 */
public final class JukeboxPipeline extends WorkStationPipeline implements TaskConfigurable {

    @Override public String taskType() { return "jukebox"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.minecraft.world.level.block.Blocks.JUKEBOX); }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("play", "播放唱片", StepType.INTERACT, List.of())); }

    /** 唱片黑白名单配置 GUI (per-maid) */
    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "jukebox");
    }

    /** 纯验证 — 仅扫描背包是否有唱片(读操作)，不写日志/通知 */
    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        // 唱片黑白名单 (per-maid 覆盖全局)
        var cfg = pipelineConfig(maid);
        var black = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_BLACKLIST),
                ActiveTaskConfig.JUKEBOX_BLACKLIST.get());
        var white = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_WHITELIST),
                ActiveTaskConfig.JUKEBOX_WHITELIST.get());
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
//? if 1.20.1 {
            if (!s.isEmpty() && s.is(ItemTags.MUSIC_DISCS) && ItemFilters.isAllowed(s, black, white)) {
//?} else {
            if (!s.isEmpty() && s.is(ItemTags.CREEPER_DROP_MUSIC_DISCS) && ItemFilters.isAllowed(s, black, white)) {
//?}
                if (target.isEmpty()) return PipelineResult.ok("");
                if (s.getDescriptionId().contains(target) || s.getItem().toString().contains(target))
                    return PipelineResult.ok("");
            }
        }
        return PipelineResult.failed(ProgressNotifier.NO_DISC);
    }

    /** 一次工作单元 — 恒 CONTINUE (永续播放, 不触发计数完成); 相位机 (原 JukeboxExecute) */
    @Override
    protected TaskResult executeOne(ServerLevel w, EntityMaid m, BlockPos p) {
        if (!(w.getBlockEntity(p) instanceof JukeboxBlockEntity jukebox)) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} pos is not a jukebox", m.getId());
            return TaskResult.CONTINUE;
        }
        CompoundTag data = m.getPersistentData();
        long now = w.getGameTime();
        int ord = MaidData.get(m, DataKey.JUKEBOX_PHASE);
        Phase phase = ord < 0 || ord >= Phase.VALUES.length ? Phase.INSERTING : Phase.VALUES[ord];
        long phaseTick = MaidData.get(m, DataKey.JUKEBOX_TICK);
        if (!data.contains(TaskKeys.JUKEBOX_PHASE)) {
            MaidData.put(m, DataKey.JUKEBOX_PHASE, Phase.INSERTING.ordinal());
            MaidData.put(m, DataKey.JUKEBOX_TICK, now);
            phase = Phase.INSERTING;
            phaseTick = now;
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} init: phase=INSERTING", m.getId());
        }
        // anti-stale — 检测异常时间戳，强制重置为 INSERTING
        if (phaseTick > now || phaseTick == 0) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} stale phaseTick={}, resetting to INSERTING", m.getId(), phaseTick);
            MaidData.put(m, DataKey.JUKEBOX_PHASE, Phase.INSERTING.ordinal());
            MaidData.put(m, DataKey.JUKEBOX_TICK, now);
            phase = Phase.INSERTING;
            phaseTick = now;
        }
        TaskStateManager.heartbeat(m, now);
        switch (phase) {
            case INSERTING -> handleInserting(w, m, p, jukebox, now);
            case PLAYING -> handlePlaying(m, jukebox, now, phaseTick);
            case EJECTING -> handleEjecting(m, jukebox);
            case PICKUP_WAIT -> handlePickupWait(m, now, phaseTick);
        }
        return TaskResult.CONTINUE;
    }

    // ── 相位处理器 (原 JukeboxExecute switch 各臂, 每相位一个顶层方法) ──

    /** INSERTING — 选碟 (黑白名单 + 目标匹配/随机) → 插碟 → PLAYING */
    private void handleInserting(ServerLevel world, EntityMaid maid, BlockPos pos,
                                 JukeboxBlockEntity jukebox, long now) {
//? if 1.20.1 {
        if (!jukebox.getFirstItem().isEmpty()) {
//?} else {
        if (!jukebox.getItem(0).isEmpty()) {
//?}
            MaidData.put(maid, DataKey.JUKEBOX_PHASE, Phase.PLAYING.ordinal());
            MaidData.put(maid, DataKey.JUKEBOX_TICK, now);
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: has disc, skip to PLAYING", maid.getId());
            return;
        }
        // 生效黑白名单 (per-maid lma_cfg_jukebox 覆盖全局) — 管线内直调 pipelineConfig (原经 TaskRegistry cast)
        CompoundTag cfg = pipelineConfig(maid);
        List<String> black = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_BLACKLIST),
                ActiveTaskConfig.JUKEBOX_BLACKLIST.get());
        List<String> white = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_WHITELIST),
                ActiveTaskConfig.JUKEBOX_WHITELIST.get());
        IItemHandler inv = maid.getAvailableInv(true);
        List<ItemStack> discs = new ArrayList<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
//? if 1.20.1 {
            if (s.is(ItemTags.MUSIC_DISCS) && ItemFilters.isAllowed(s, black, white)) discs.add(s);
//?} else {
            if (s.is(ItemTags.CREEPER_DROP_MUSIC_DISCS) && ItemFilters.isAllowed(s, black, white)) discs.add(s);
//?}
        }
        if (discs.isEmpty()) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: no discs in inventory", maid.getId());
            return;
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
                return;
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
            MaidData.put(maid, DataKey.JUKEBOX_PHASE, Phase.PLAYING.ordinal());
            MaidData.put(maid, DataKey.JUKEBOX_TICK, now);
            MaidChatBubbleApi.showInfo(maid, "正在播放: " + chosen.getHoverName().getString());
            int wait = ActiveTaskConfig.JUKEBOX_WAIT_TICKS.get();
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING→PLAYING (wait {} ticks={}min)",
                maid.getId(), wait, wait / 20 / 60);
        }
    }

    /** PLAYING — 播放中, 空 → INSERTING; 到时 → EJECTING */
    private void handlePlaying(EntityMaid maid, JukeboxBlockEntity jukebox, long now, long phaseTick) {
//? if 1.20.1 {
        if (jukebox.getFirstItem().isEmpty()) {
//?} else {
        if (jukebox.getItem(0).isEmpty()) {
//?}
            MaidData.put(maid, DataKey.JUKEBOX_PHASE, Phase.INSERTING.ordinal());
            MaidData.put(maid, DataKey.JUKEBOX_TICK, now);
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING: empty, back to INSERTING", maid.getId());
            return;
        }
        long elapsed = Math.abs(now - phaseTick);
        if (elapsed >= ActiveTaskConfig.JUKEBOX_WAIT_TICKS.get()) {
            MaidData.put(maid, DataKey.JUKEBOX_PHASE, Phase.EJECTING.ordinal());
            MaidData.put(maid, DataKey.JUKEBOX_TICK, now);
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING→EJECTING (elapsed {} ticks)",
                maid.getId(), elapsed);
        }
    }

    /** EJECTING — 弹出 → PICKUP_WAIT; 空 → INSERTING */
    private void handleEjecting(EntityMaid maid, JukeboxBlockEntity jukebox) {
        boolean ejected = JukeboxService.ejectDisc(jukebox, maid);
        long now = maid.level().getGameTime();
        if (ejected) {
            MaidData.put(maid, DataKey.JUKEBOX_PHASE, Phase.PICKUP_WAIT.ordinal());
            MaidData.put(maid, DataKey.JUKEBOX_TICK, now);
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} EJECTING: disc ejected", maid.getId());
        } else {
            MaidData.put(maid, DataKey.JUKEBOX_PHASE, Phase.INSERTING.ordinal());
            MaidData.put(maid, DataKey.JUKEBOX_TICK, now);
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} EJECTING: empty, back to INSERTING", maid.getId());
        }
    }

    /** PICKUP_WAIT — 拾取等待计时, 到时 → INSERTING */
    private void handlePickupWait(EntityMaid maid, long now, long phaseTick) {
        long elapsed = Math.abs(now - phaseTick);
        if (elapsed >= VanillaConstants.JUKEBOX_PICKUP_TICKS) {
            MaidData.put(maid, DataKey.JUKEBOX_PHASE, Phase.INSERTING.ordinal());
            MaidData.put(maid, DataKey.JUKEBOX_TICK, now);
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PICKUP_WAIT→INSERTING", maid.getId());
        }
    }

    /** 播放相位 (原 JukeboxExecute.Phase 收编) — 状态随任务键跨拍持久 */
    private enum Phase {
        INSERTING, PLAYING, EJECTING, PICKUP_WAIT;
        private static final Phase[] VALUES = values();
    }
}
