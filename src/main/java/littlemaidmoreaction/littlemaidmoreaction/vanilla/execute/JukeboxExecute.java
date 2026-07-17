package littlemaidmoreaction.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.VanillaConstants;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.block.JukeboxOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.items.IItemHandler;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** v29: 唱片机编排 — enum状态机, INSERTING→PLAYING→EJECTING→PICKUP_WAIT→INSERTING */
public final class JukeboxExecute {
    private static final int PLAY_TICKS = VanillaConstants.JUKEBOX_PLAY_TICKS;
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
        data.putLong("lma_flow_tick", now);

        switch (phase) {
            case INSERTING -> {
                if (!jukebox.getFirstItem().isEmpty()) {
                    data.putInt("lma_jukebox_phase", Phase.PLAYING.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: has disc, skip to PLAYING", maid.getId());
                    return false;
                }
                IItemHandler inv = maid.getAvailableInv(true);
                List<ItemStack> discs = new ArrayList<>();
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (s.is(ItemTags.MUSIC_DISCS)) discs.add(s);
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
                var discKey = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(chosen.getItem());
                LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: chose {}/{} from {} discs",
                    maid.getId(), discKey, chosen.getDisplayName().getString(), discs.size());

                IItemHandler maidInv = maid.getAvailableInv(true);
                boolean inserted = false;
                for (int i = 0; i < maidInv.getSlots(); i++) {
                    if (ItemStack.isSameItemSameTags(maidInv.getStackInSlot(i), chosen)) {
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
                    maid.getChatBubbleManager().addTextChatBubble(
                        "正在播放: " + chosen.getHoverName().getString());
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING→PLAYING ({} ticks={}min)",
                        maid.getId(), PLAY_TICKS, PLAY_TICKS / 20 / 60);
                }
                return inserted;
            }
            case PLAYING -> {
                if (jukebox.getFirstItem().isEmpty()) {
                    data.putInt("lma_jukebox_phase", Phase.INSERTING.ordinal());
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING: empty, back to INSERTING", maid.getId());
                    return false;
                }
                long elapsed = Math.abs(now - phaseTick);
                if (elapsed >= PLAY_TICKS) {
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
