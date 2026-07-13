package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.output;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** YSM 模型输出原语 */
public final class YsmWriter {
    private YsmWriter() {}

    private record YsmEntry(String modelId, String displayName, List<String> textures) {}
    private static final YsmEntry[] YSM_MODELS = {
        e("wine_fox/01_taisho_maid","大正女仆酒狐","skin","skin_white"),
        e("wine_fox/02_new_year","新年酒狐","skin"), e("wine_fox/03_astronaut","宇航员酒狐","skin"),
        e("wine_fox/04_kongfu","功夫酒狐","skin"), e("wine_fox/05_magical","魔法酒狐","skin"),
        e("wine_fox/06_hanfu","汉服酒狐","skin"), e("wine_fox/07_jk","JK酒狐","skin"),
        e("wine_fox/08_sta","STA酒狐","skin"), e("wine_fox/09_hailuo","海螺酒狐","skin"),
        e("wine_fox/10_zhiban","值班酒狐","skin"), e("wine_fox/11_salesperson","销售员酒狐","skin"),
        e("wine_fox/12_little","小酒狐","skin"), e("wine_fox/13_matured","成熟酒狐","skin"),
        e("wine_fox/14_momo","桃酒狐","skin"), e("wine_fox/15_kluonoa","克鲁诺亚酒狐","skin"),
        e("wine_fox/16_tactics","战术酒狐","skin"), e("wine_fox/17_mini","迷你酒狐","skin"),
        e("wine_fox/18_wedding","婚纱酒狐","skin"), e("wine_fox/19_nine_tailed","九尾酒狐","skin"),
        e("wine_fox/20_survivor","幸存者酒狐","skin"), e("wine_fox/21_saint","圣者酒狐","skin"),
        e("wine_fox/22_elf","精灵酒狐","skin"),
    };
    private static YsmEntry e(String modelId, String displayName, String... textures) {
        return new YsmEntry(modelId, displayName, List.of(textures));
    }

    public static void disableModel(EntityMaid maid) { maid.setIsYsmModel(false); }
    public static void playRoulette(EntityMaid maid, String animName) { maid.playRouletteAnim(animName); }
    public static void stopRoulette(EntityMaid maid) { if (maid.isYsmModel()) maid.stopRouletteAnim(); }
    public static void resetRoamingVars(EntityMaid maid) { maid.roamingVars.clear(); maid.roamingVarsUpdateFlag++; }
    public static void setRoamingVar(EntityMaid maid, String name, float value) { maid.roamingVars.put(name, value); maid.roamingVarsUpdateFlag++; }

    /** 设置YSM模型 — mode="ysm女仆模型"=随机, 否则手动输入 */
    public static void setModel(EntityMaid maid, String mode, String modelId, String texture, String modelName) {
        var rng = ThreadLocalRandom.current();
        String mid, tex; Component name;
        if ("ysm女仆模型".equals(mode)) {
            var entry = YSM_MODELS[rng.nextInt(YSM_MODELS.length)];
            mid = entry.modelId;
            tex = entry.textures.get(rng.nextInt(entry.textures.size()));
            name = Component.literal(entry.displayName);
        } else {
            if (modelId.isEmpty()) return;
            mid = pickRandom(modelId, rng);
            tex = texture.isEmpty() ? "skin" : pickRandom(texture, rng);
            String[] names = modelName.split(",");
            String pickedName = names[rng.nextInt(names.length)].trim();
            name = pickedName.isEmpty() ? (maid.getYsmModelName() != null ? maid.getYsmModelName() : Component.literal("")) : Component.literal(pickedName);
        }
        maid.setYsmModel(mid, tex, name);
        maid.setIsYsmModel(true);
    }

    private static String pickRandom(String csv, ThreadLocalRandom rng) {
        String[] parts = csv.split(",");
        return parts.length == 1 ? parts[0].trim() : parts[rng.nextInt(parts.length)].trim();
    }
}
