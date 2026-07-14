package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.movement.BrainHelper;
import littlemaidmoreaction.littlemaidmoreaction.core.model.LmaAnimationDef;
import littlemaidmoreaction.littlemaidmoreaction.network.LmaAnimSyncMessage;
import littlemaidmoreaction.littlemaidmoreaction.api.AnimationDurationManager;
import littlemaidmoreaction.littlemaidmoreaction.storage.LmaAnimationStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 动画播放编排 — 委托 PlayAnimAction (241行→本类)。
 * <p>INSTANT: 单动画 → PersistentData → magic_casting 驱动
 * FULL: 三阶段动画 → tick 驱动阶段切换 (由客户端 LmaMagicCastingProvider 执行)
 */
public final class AnimExecute {
    private AnimExecute() {}

    /** @return true 表示动画已写入 PersistentData */
    public static boolean execute(ServerLevel world, EntityMaid maid,
            String mode, String anim, String animStart, String animCasting, String animEnd,
            String durStart, String durCasting, String durEnd, boolean autoWait) {
        var data = maid.getPersistentData();
        long gameTime = world.getGameTime();
        int maidId = maid.getId();

        if ("FULL".equals(mode)) {
            return executeFull(maid, data, gameTime, maidId,
                animStart, animCasting, animEnd, durStart, durCasting, durEnd, autoWait);
        }
        return executeInstant(maid, data, gameTime, maidId, anim, autoWait);
    }

    private static boolean executeInstant(EntityMaid maid, CompoundTag data, long gameTime,
            int maidId, String anim, boolean autoWait) {
        String animName = pickRandom(anim != null ? anim : "");
        if (animName.isEmpty()) return false;

        if (maid.isYsmModel()) {
            maid.playRouletteAnim(animName);
            data.putString("lma_anim_mode", "YSM_ROULETTE");
            data.putString("lma_anim", animName);
            data.putLong("lma_anim_tick", gameTime);
            data.putInt("lma_anim_dur", 20);
            if (autoWait) { BrainHelper.freeze(maid); data.putInt("lma_wait_ticks", 20); }
            sync(maid, data, "lma_anim", "lma_anim_mode", "lma_anim_tick", "lma_anim_dur");
            return true;
        }

        LmaAnimationDef def = LmaAnimationStorage.get(animName).orElse(LmaAnimationDef.fallback(animName));
        int seq = safeIncrementSeq(data.getInt("lma_anim_seq"));
        int animDur = AnimationDurationManager.getAnimationDuration(animName);

        data.putString("lma_anim", animName);
        data.putString("lma_anim_mode", "INSTANT");
        data.putString("lma_anim_phase", "INSTANT");
        data.putInt("lma_anim_seq", seq);
        data.putLong("lma_anim_tick", gameTime);
        data.putInt("lma_anim_dur", animDur);
        data.putBoolean("lma_lock_move", def.lockMovement());
        data.putString("lma_anim_priority", String.valueOf(def.priority()));
        sync(maid, data, "lma_anim", "lma_anim_mode", "lma_anim_phase",
            "lma_anim_seq", "lma_anim_tick", "lma_anim_dur", "lma_lock_move", "lma_anim_priority");

        if (autoWait || def.freezeAI()) BrainHelper.freeze(maid);
        if (autoWait) data.putInt("lma_wait_ticks", animDur > 0 ? animDur : 40);
        return true;
    }

    private static boolean executeFull(EntityMaid maid, CompoundTag data, long gameTime,
            int maidId, String animStart, String animCasting, String animEnd,
            String durStart, String durCasting, String durEnd, boolean autoWait) {
        String start = pickRandom(animStart);
        if (start.isEmpty()) return false;
        String casting = pickRandom(animCasting);
        String end = pickRandom(animEnd);

        int seq = safeIncrementSeq(data.getInt("lma_anim_seq"));
        LmaAnimationDef defStart = LmaAnimationStorage.get(start).orElse(LmaAnimationDef.fallback(start));

        data.putString("lma_anim_mode", "FULL");
        data.putInt("lma_anim_seq", seq);
        data.putLong("lma_anim_tick", gameTime);
        data.putString("lma_anim_start", start);
        data.putString("lma_anim_casting", casting);
        data.putString("lma_anim_end", end);
        data.putString("lma_anim_phase", "START");
        data.putInt("lma_dur_start", parseInt(durStart, 20));
        data.putInt("lma_dur_casting", parseInt(durCasting, 20));
        data.putInt("lma_dur_end", parseInt(durEnd, 20));
        data.putString("lma_anim_priority", String.valueOf(defStart.priority()));
        data.putBoolean("lma_lock_move", defStart.lockMovement());
        sync(maid, data, "lma_anim_mode", "lma_anim_seq", "lma_anim_tick",
            "lma_anim_start", "lma_anim_casting", "lma_anim_end", "lma_anim_phase",
            "lma_dur_start", "lma_dur_casting", "lma_dur_end", "lma_anim_priority", "lma_lock_move");

        if (autoWait || defStart.freezeAI()) BrainHelper.freeze(maid);
        if (autoWait) {
            int ds = Math.max(1, parseInt(durStart, 20));
            int dc = Math.max(1, parseInt(durCasting, 20));
            data.putInt("lma_wait_ticks", ds + dc);
        }
        return true;
    }

    // === helpers ===
    static String pickRandom(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        var parts = raw.split(",");
        if (parts.length == 0) return "";
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        var valid = Arrays.stream(parts).filter(s -> !s.isEmpty()).toList();
        if (valid.isEmpty()) return "";
        return valid.size() == 1 ? valid.get(0) : valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.isEmpty() ? String.valueOf(def) : s); }
        catch (NumberFormatException e) { return def; }
    }

    private static int safeIncrementSeq(int current) {
        return current >= Integer.MAX_VALUE - 1 ? 1 : current + 1;
    }

    private static void sync(EntityMaid maid, CompoundTag src, String... keys) {
        var tag = new CompoundTag();
        for (String k : keys) if (src.contains(k)) tag.put(k, src.get(k).copy());
        LmaAnimSyncMessage.sendToTracking(maid, tag);
    }
}
