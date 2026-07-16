package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

public final class ProgressNotifier {
    private static final System.Logger LOG = System.getLogger("LMA-V16-ProgressNotifier");

    public static final String NO_WORKBENCH = "周围没有工作台";
    public static final String NO_FURNACE = "周围没有熔炉";
    public static final String NO_JUKEBOX = "周围没有唱片机";
    public static final String NO_BELL = "周围没有钟";
    public static final String NO_ALTAR = "周围没有祭坛";
    public static final String NO_FUEL = "缺少燃料";
    public static final String NO_DISC = "背包里没有唱片";
    public static final String CRAFT_DONE = "合成完成";
    public static final String SMELT_DONE = "烧炼完成";
    public static final String BELL_DONE = "敲钟完成";
    public static final String ALTAR_DONE = "祭坛合成完成";
    public static final String BACKPACK_FULL = "背包已满，产物掉落在地上";

    public static String missing(String itemName, int count) { return "缺少" + itemName + "，需要" + count + "个"; }
    public static String depleted(String itemName, int needMore) { return itemName + "用完了，还需要" + needMore + "个"; }
    public static String depleted(String itemName) { return itemName + "用完了，无法继续"; }
    public static String noSmeltable(String itemName) { return "没有" + itemName + "可以烧"; }
    public static String nowPlaying(String discName) { return "正在播放: " + discName; }

    private ProgressNotifier() {}

    public static void notify(EntityMaid maid, String message) {
        LOG.log(System.Logger.Level.INFO, "[V16] [Progress] notify: {0}", message);
        maid.getPersistentData().putString("lma_bubble_msg", message);
        maid.getPersistentData().putLong("lma_bubble_tick", maid.level().getGameTime());
    }
}
