package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemStackHelper;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.movement.BrainHelper;
import com.github.xiaozhaoz1.littlemaidmoreaction.core.model.LmaAnimationDef;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.LmaAnimSyncMessage;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationDurationManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.LmaAnimationStorage;
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
            com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmOutput.playRoulette(maid, animName);
            data.putString(TaskKeys.ANIM_MODE, "YSM_ROULETTE");
            data.putString(TaskKeys.ANIM_NAME, animName);
            data.putLong(TaskKeys.ANIM_TICK, gameTime);
            data.putInt(TaskKeys.ANIM_DUR, 20);
            if (autoWait) { BrainHelper.freeze(maid); data.putInt(TaskKeys.WAIT_TICKS, 20); }
            sync(maid, data, TaskKeys.ANIM_NAME, TaskKeys.ANIM_MODE, TaskKeys.ANIM_TICK, TaskKeys.ANIM_DUR);
            return true;
        }

        LmaAnimationDef def = LmaAnimationStorage.get(animName).orElse(LmaAnimationDef.fallback(animName));
        int seq = safeIncrementSeq(data.getInt(TaskKeys.ANIM_SEQ));
        int animDur = AnimationDurationManager.getAnimationDuration(animName);

        data.putString(TaskKeys.ANIM_NAME, animName);
        data.putString(TaskKeys.ANIM_MODE, "INSTANT");
        data.putString(TaskKeys.ANIM_PHASE, "INSTANT");
        data.putInt(TaskKeys.ANIM_SEQ, seq);
        data.putLong(TaskKeys.ANIM_TICK, gameTime);
        data.putInt(TaskKeys.ANIM_DUR, animDur);
        data.putBoolean(TaskKeys.LOCK_MOVE, def.lockMovement());
        data.putString(TaskKeys.ANIM_PRIORITY, String.valueOf(def.priority()));
        sync(maid, data, TaskKeys.ANIM_NAME, TaskKeys.ANIM_MODE, TaskKeys.ANIM_PHASE,
            TaskKeys.ANIM_SEQ, TaskKeys.ANIM_TICK, TaskKeys.ANIM_DUR, TaskKeys.LOCK_MOVE, TaskKeys.ANIM_PRIORITY);

        if (autoWait || def.freezeAI()) BrainHelper.freeze(maid);
        if (autoWait) data.putInt(TaskKeys.WAIT_TICKS, animDur > 0 ? animDur : 40);
        return true;
    }

    private static boolean executeFull(EntityMaid maid, CompoundTag data, long gameTime,
            int maidId, String animStart, String animCasting, String animEnd,
            String durStart, String durCasting, String durEnd, boolean autoWait) {
        String start = pickRandom(animStart);
        if (start.isEmpty()) return false;
        String casting = pickRandom(animCasting);
        String end = pickRandom(animEnd);

        // v79.18: YSM 分流 (executeInstant 同款) — YSM 模型渲染不吃 TLM ISS 动画,
        // FULL 语义 = 循环播放 → YsmOutput.playRoulette (循环由动画文件 loop 决定, YSM 管理停止)
        if (maid.isYsmModel()) {
            com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmOutput.playRoulette(maid, start);
            data.putString(TaskKeys.ANIM_MODE, "YSM_ROULETTE");
            data.putString(TaskKeys.ANIM_NAME, start);
            data.putLong(TaskKeys.ANIM_TICK, gameTime);
            data.putInt(TaskKeys.ANIM_DUR, 20);
            if (autoWait) { BrainHelper.freeze(maid); data.putInt(TaskKeys.WAIT_TICKS, 20); }
            sync(maid, data, TaskKeys.ANIM_NAME, TaskKeys.ANIM_MODE, TaskKeys.ANIM_TICK, TaskKeys.ANIM_DUR);
            return true;
        }

        int seq = safeIncrementSeq(data.getInt(TaskKeys.ANIM_SEQ));
        LmaAnimationDef defStart = LmaAnimationStorage.get(start).orElse(LmaAnimationDef.fallback(start));

        data.putString(TaskKeys.ANIM_MODE, "FULL");
        data.putInt(TaskKeys.ANIM_SEQ, seq);
        data.putLong(TaskKeys.ANIM_TICK, gameTime);
        data.putString(TaskKeys.ANIM_START, start);
        data.putString(TaskKeys.ANIM_CASTING, casting);
        data.putString(TaskKeys.ANIM_END, end);
        data.putString(TaskKeys.ANIM_PHASE, "START");
        data.putInt(TaskKeys.DUR_START, parseInt(durStart, 20));
        data.putInt(TaskKeys.DUR_CASTING, parseInt(durCasting, 20));
        data.putInt(TaskKeys.DUR_END, parseInt(durEnd, 20));
        data.putString(TaskKeys.ANIM_PRIORITY, String.valueOf(defStart.priority()));
        data.putBoolean(TaskKeys.LOCK_MOVE, defStart.lockMovement());
        sync(maid, data, TaskKeys.ANIM_MODE, TaskKeys.ANIM_SEQ, TaskKeys.ANIM_TICK,
            TaskKeys.ANIM_START, TaskKeys.ANIM_CASTING, TaskKeys.ANIM_END, TaskKeys.ANIM_PHASE,
            TaskKeys.DUR_START, TaskKeys.DUR_CASTING, TaskKeys.DUR_END, TaskKeys.ANIM_PRIORITY, TaskKeys.LOCK_MOVE);

        if (autoWait || defStart.freezeAI()) BrainHelper.freeze(maid);
        if (autoWait) {
            int ds = Math.max(1, parseInt(durStart, 20));
            int dc = Math.max(1, parseInt(durCasting, 20));
            data.putInt(TaskKeys.WAIT_TICKS, ds + dc);
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
