package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.jukebox;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.block.JukeboxOutput;
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

/** v23: 唱片机编排 — 4态状态机, INSERTING→PLAYING(5min)→EJECTING→PICKUP_WAIT(1s)→INSERTING */
public final class JukeboxExecute {
    private static final int PLAY_TICKS = 6000;
    private static final int PICKUP_TICKS = 20;

    private JukeboxExecute() {}

    /** @return true if meaningful action was performed, false if no-op */
    public static boolean execute(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof JukeboxBlockEntity jukebox)) {
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} pos is not a jukebox", maid.getId());
            return false;
        }

        CompoundTag data = maid.getPersistentData();
        String phase = data.getString("lma_jukebox_phase");
        long phaseTick = data.getLong("lma_jukebox_tick");
        long now = world.getGameTime();

        if (phase.isEmpty()) {
            phase = "INSERTING";
            data.putString("lma_jukebox_phase", phase);
            data.putLong("lma_jukebox_tick", now);
            phaseTick = now;
            LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} init: phase=INSERTING", maid.getId());
        }
        data.putLong("lma_flow_tick", now);  // 防 TaskEngine 超时

        switch (phase) {
            case "INSERTING" -> {
                // 唱片机已有碟 → 跳到 PLAYING (可能是手动放入)
                if (!jukebox.getFirstItem().isEmpty()) {
                    data.putString("lma_jukebox_phase", "PLAYING");
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: jukebox already has disc, skip to PLAYING", maid.getId());
                    return false;
                }
                IItemHandler inv = maid.getAvailableInv(true);
                List<ItemStack> discs = new ArrayList<>();
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (s.is(ItemTags.MUSIC_DISCS)) discs.add(s);
                }
                if (discs.isEmpty()) {
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: no music discs in inventory", maid.getId());
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
                        LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: disc '{}' not found in inventory", maid.getId(), target);
                        return false;
                    }
                } else {
                    chosen = discs.get(ThreadLocalRandom.current().nextInt(discs.size()));
                }
                var discKey = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(chosen.getItem());
                LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING: chose {}/{} from {} discs",
                    maid.getId(), discKey, chosen.getDisplayName().getString(), discs.size());

                // 从女仆背包提取并插入唱片机
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
                    data.putString("lma_jukebox_phase", "PLAYING");
                    data.putLong("lma_jukebox_tick", now);
                    maid.getChatBubbleManager().addTextChatBubble(
                        "正在播放: " + chosen.getHoverName().getString());
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} INSERTING→PLAYING ({} ticks={}min)",
                        maid.getId(), PLAY_TICKS, PLAY_TICKS / 20 / 60);
                }
                return inserted;
            }
            case "PLAYING" -> {
                // ★ Bug A: 检测唱片被手动取出 或 走到了空唱片机
                if (jukebox.getFirstItem().isEmpty()) {
                    data.putString("lma_jukebox_phase", "INSERTING");
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING: jukebox empty, back to INSERTING", maid.getId());
                    return false;
                }
                if (now - phaseTick >= PLAY_TICKS) {
                    data.putString("lma_jukebox_phase", "EJECTING");
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PLAYING→EJECTING (finished {} ticks)",
                        maid.getId(), now - phaseTick);
                }
                return false;
            }
            case "EJECTING" -> {
                boolean ejected = JukeboxOutput.ejectDisc(jukebox, maid);
                if (ejected) {
                    data.putString("lma_jukebox_phase", "PICKUP_WAIT");
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} EJECTING: disc ejected", maid.getId());
                } else {
                    data.putString("lma_jukebox_phase", "INSERTING");
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} EJECTING: jukebox empty, back to INSERTING", maid.getId());
                }
                return ejected;
            }
            case "PICKUP_WAIT" -> {
                if (now - phaseTick >= PICKUP_TICKS) {
                    data.putString("lma_jukebox_phase", "INSERTING");
                    data.putLong("lma_jukebox_tick", now);
                    LittleMaidMoreAction.LOGGER.debug("[Jukebox] maid={} PICKUP_WAIT→INSERTING (disc collected)", maid.getId());
                }
                return false;
            }
        }
        return false;
    }
}
