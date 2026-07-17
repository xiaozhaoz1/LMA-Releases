package littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ConcurrentHashMap;

/**
 * FakePlayer 生命周期管理器 — 管理持续模式 (左键持续挖掘)。
 *
 * <p>维护 maidId → FakePlayerTask 映射, 通过 {@code @EventBusSubscriber}
 * 在每个 ServerTick 驱动活跃任务。</p>
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class FakePlayerManager {
    private static final ConcurrentHashMap<Integer, FakePlayerTask> TASKS = new ConcurrentHashMap<>();

    public record FakePlayerTask(LmaFakePlayer player, BlockPos targetPos, Direction face,
                                  LmaPlayerSimulator.Mode mode) {}

    /** 开始持续操作 */
    public static FakePlayerTask start(EntityMaid maid, BlockPos pos, Direction face,
                                        LmaPlayerSimulator.Mode mode) {
        if (!(maid.level() instanceof ServerLevel sw)) return null;
        LmaFakePlayer fp = new LmaFakePlayer(sw, maid, pos);
        FakePlayerTask task = new FakePlayerTask(fp, pos, face, mode);
        TASKS.put(maid.getId(), task);
        return task;
    }

    /** 停止并清理 */
    public static void stop(EntityMaid maid) {
        FakePlayerTask task = TASKS.remove(maid.getId());
        if (task != null && task.player.level() instanceof ServerLevel sw) {
            LmaPlayerSimulator.syncHandToMaid(task.player);
            LmaPlayerSimulator.cleanup(task.player, sw);
        }
    }

    /** 女仆卸载时清理 */
    public static void onMaidUnload(int maidId) {
        FakePlayerTask task = TASKS.remove(maidId);
        if (task != null && task.player.level() instanceof ServerLevel sw) {
            LmaPlayerSimulator.cleanup(task.player, sw);
        }
    }

    /** 是否有活跃任务 */
    public static boolean hasTask(EntityMaid maid) {
        return TASKS.containsKey(maid.getId());
    }

    // ── 全局 tick 驱动 ──

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) return;

        for (var entry : TASKS.entrySet()) {
            FakePlayerTask task = entry.getValue();
            if (!task.player.isAlive()) {
                TASKS.remove(entry.getKey());
                continue;
            }
            ServerLevel level = task.player.serverLevel();
            if (level == null) continue;
            if (level.getEntity(entry.getKey()) instanceof EntityMaid maid) {
                LmaPlayerSimulator.simulate(task.player, level, task.targetPos, task.face, task.mode);
                if (task.mode == LmaPlayerSimulator.Mode.LEFT_CLICK_CONTINUOUS
                    && level.getBlockState(task.targetPos).isAir()) {
                    stop(maid);
                }
            }
        }
    }
}
