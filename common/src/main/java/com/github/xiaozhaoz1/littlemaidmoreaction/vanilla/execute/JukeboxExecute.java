package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ItemFilters;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.block.JukeboxOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
//?}

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * v29: 唱片机编排 — enum状态机, INSERTING→PLAYING→EJECTING→PICKUP_WAIT→INSERTING
 * v67.3: 播放等待时长 Cloth Config (jukebox.wait_ticks), 选碟黑白名单 (全局 + per-maid)。
 */
public final class JukeboxExecute {
    private static final int PICKUP_TICKS = VanillaConstants.JUKEBOX_PICKUP_TICKS;

    enum Phase {
        INSERTING, PLAYING, EJECTING, PICKUP_WAIT;
        private static final Phase[] VALUES = values();
        static Phase fromOrdinal(int ord) {
            if (ord < 0 || ord >= VALUES.length) return INSERTING;
            return VALUES[ord];
        }
    }

    private JukeboxExecute() {}

    /** @return true if meaningful action was performed */
    public static boolean execute(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof JukeboxBlockEntity jukebox)) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} pos is not a jukebox", maid.getId());
            return false;
        }

        // v67.3: 生效黑白名单 (per-maid lma_cfg_jukebox 覆盖全局)
        CompoundTag cfg = TaskRegistry.get("jukebox").pipeline().pipelineConfig(maid);
        List<String> black = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_BLACKLIST),
                ActiveTaskConfig.JUKEBOX_BLACKLIST.get());
        List<String> white = ItemFilters.effective(ItemFilters.maidList(cfg, ItemFilters.KEY_WHITELIST),
                ActiveTaskConfig.JUKEBOX_WHITELIST.get());

        CompoundTag data = maid.getPersistentData();
        Phase phase = Phase.fromOrdinal(data.getInt("lma_jukebox_phase"));
        long phaseTick = data.getLong("lma_jukebox_tick");
        long now = world.getGameTime();

        if (!data.contains("lma_jukebox_phase")) {
            data.putInt("lma_jukebox_phase", Phase.INSERTING.ordinal());
            data.putLong("lma_jukebox_tick", now);
            phase = Phase.INSERTING;
            phaseTick = now;
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} init: phase=INSERTING", maid.getId());
        }
        // v53: anti-stale — 检测异常时间戳，强制重置为 INSERTING
        if (phaseTick > now || phaseTick == 0) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} stale phaseTick={}, resetting to INSERTING", maid.getId(), phaseTick);
            data.putInt("lma_jukebox_phase", Phase.INSERTING.ordinal());
            data.putLong("lma_jukebox_tick", now);
            phase = Phase.INSERTING;
            phaseTick = now;
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager.heartbeat(maid, now);

        switch (phase) {
            case INSERTING -> {
//? if 1.20.1 {
                if (!jukebox.getFirstItem().isEmpty()) {
//?} else {
                if (!jukebox.getItem(0).isEmpty()) {
//?}
                    data.putInt("lma_jukebox_phase", Phase.PLAYING.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: has disc, skip to PLAYING", maid.getId());
                    return false;
                }
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
                    return false;
                }

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
                        return false;
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
                            JukeboxOutput.insertDisc(jukebox, extracted, world, pos);
                            inserted = true;
                            break;
                        }
                    }
                }
                if (inserted) {
                    data.putInt("lma_jukebox_phase", Phase.PLAYING.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    // v79.21: 统一信息气泡 API
                    MaidChatBubbleApi.showInfo(maid,
                        "正在播放: " + chosen.getHoverName().getString());
                    int wait = ActiveTaskConfig.JUKEBOX_WAIT_TICKS.get();
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING→PLAYING (wait {} ticks={}min)",
                        maid.getId(), wait, wait / 20 / 60);
                }
                return inserted;
            }
            case PLAYING -> {
//? if 1.20.1 {
                if (jukebox.getFirstItem().isEmpty()) {
//?} else {
                if (jukebox.getItem(0).isEmpty()) {
//?}
                    data.putInt("lma_jukebox_phase", Phase.INSERTING.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING: empty, back to INSERTING", maid.getId());
                    return false;
                }
                long elapsed = Math.abs(now - phaseTick);
                if (elapsed >= ActiveTaskConfig.JUKEBOX_WAIT_TICKS.get()) {
                    data.putInt("lma_jukebox_phase", Phase.EJECTING.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING→EJECTING (elapsed {} ticks)",
                        maid.getId(), elapsed);
                }
                return false;
            }
            case EJECTING -> {
                boolean ejected = JukeboxOutput.ejectDisc(jukebox, maid);
                if (ejected) {
                    data.putInt("lma_jukebox_phase", Phase.PICKUP_WAIT.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} EJECTING: disc ejected", maid.getId());
                } else {
                    data.putInt("lma_jukebox_phase", Phase.INSERTING.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} EJECTING: empty, back to INSERTING", maid.getId());
                }
                return ejected;
            }
            case PICKUP_WAIT -> {
                long elapsed = Math.abs(now - phaseTick);
                if (elapsed >= PICKUP_TICKS) {
                    data.putInt("lma_jukebox_phase", Phase.INSERTING.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PICKUP_WAIT→INSERTING", maid.getId());
                }
                return false;
            }
        }
        return false;
    }
}
