package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.search;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class LightQuery {
    public static final int LOW_BRIGHTNESS = 9;

    private LightQuery() {}

    public static int getBrightness(Level level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos);
    }

    public static boolean isDarkEnough(Level level, BlockPos pos) {
        return getBrightness(level, pos) < LOW_BRIGHTNESS;
    }

    public static String describe(Level level, BlockPos pos) {
        int light = getBrightness(level, pos);
        String danger = light < LOW_BRIGHTNESS ? " (DARK! mobs can spawn)" : "";
        return String.format("light=%d/15%s", light, danger);
    }
}
