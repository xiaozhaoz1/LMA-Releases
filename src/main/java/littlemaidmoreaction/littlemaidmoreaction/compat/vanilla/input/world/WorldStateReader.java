package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** 世界状态读取 — 覆盖 ~21 个 {@code impl/condition/world/} 查询 */
public final class WorldStateReader {
    private WorldStateReader() {}

    public static String getDimension(Level w) { return w.dimension().location().toString(); }
    public static long getTime(Level w) { return w.getDayTime() % 24000; }
    public static boolean isDay(Level w) { return w.isDay(); }
    public static boolean isNight(Level w) { return !w.isDay(); }
    public static boolean isRaining(Level w) { return w.isRaining(); }
    public static boolean isThundering(Level w) { return w.isThundering(); }
    public static int getMoonPhase(Level w) { return w.getMoonPhase(); }
    public static int getDifficulty(Level w) { return w.getDifficulty().getId(); }
    public static String getBiome(Level w, BlockPos pos) {
        var biome = w.getBiome(pos); return biome != null ? biome.unwrapKey().map(k -> k.location().toString()).orElse("unknown") : "unknown";
    }
    public static int getLightLevel(Level w, BlockPos pos) {
        return w.getMaxLocalRawBrightness(pos);
    }
    public static boolean hasDaylight(Level w, BlockPos pos) { return w.canSeeSky(pos); }
    public static boolean isCriticalAttack(DamageSource src) {
        return src.getEntity() instanceof net.minecraft.world.entity.player.Player && ((net.minecraft.world.entity.player.Player) src.getEntity()).getAttackStrengthScale(0.5F) > 0.9F;
    }
    public static boolean bypassesArmor(DamageSource src) { return src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR); }
    public static String getDamageType(DamageSource src) { return src.getMsgId(); }

    // === Phase 7: 实体查找 ===
    /** 通过 UUID 获取在线玩家 */
    public static Player getPlayerByUUID(Level level, UUID uuid) { return level.getPlayerByUUID(uuid); }

    // === Phase 12: 基础世界查询 ===
    /** 获取方块状态 */
    public static net.minecraft.world.level.block.state.BlockState getBlockState(Level world, BlockPos pos) { return world.getBlockState(pos); }
    /** 获取范围内的实体 */
    public static <T extends net.minecraft.world.entity.Entity> List<T> getEntitiesInRange(Level world, BlockPos center, double range, Class<T> clazz, Predicate<T> filter) {
        return world.getEntitiesOfClass(clazz, new net.minecraft.world.phys.AABB(center).inflate(range), filter);
    }
}
