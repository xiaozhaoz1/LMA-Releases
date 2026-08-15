package com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * YSM 模型输出原语 (v73) — IO 架构对应方法 (vanilla/output 分层, MaidStateWriter 模式)。
 *
 * <p>YSM 能力全在 TLM EntityMaid 内建集成 (setYsmModel/isYsmModel/playRouletteAnim/
 * roamingVars) — 零 ysm jar 依赖, 仅需 ModList 门控 (yes_steve_model)。
 * 方法逐字迁移自旧仓库 compat/ysm/output/YsmWriter (求值语义不变)。
 *
 * <p>v79.18: 由 vanilla/output/ysm/ 迁入 compat/ysm/ (YSM = 外部兼容模块, 属 compat 层)。</p>
 */
public final class YsmOutput {

    private YsmOutput() {}

    // ── 内置 YSM 模型预设 (22 酒狐 — 迁移自旧 YsmWriter) ──

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

    // ── IO 方法 ──

    /**
     * 播放 YSM 轮盘动画 (实战校准 — 哈气动画实证).
     *
     * <p><b>播放前提 (三件套缺一不播, OpenYSM 2.6.6 反编译 + 用户实测)</b>:
     * ① 模型包 {@code animations/extra.animation.json} 含同名动画定义 (YsmAnimInjector 注入)
     * ② 模型包 {@code ysm.json} 的 {@code properties.extra_animation} 含同名声明表项
     * ③ 女仆 {@code isYsmModel() == true} (AnimExecute 已分流 — 调用方保证)
     * 服务端调用 → SyncYsmMaidDataMessage → YSM mod 查表播放。
     */
    public static void playRoulette(EntityMaid maid, String animName) {
        maid.playRouletteAnim(animName);
    }

    /**
     * 停止轮盘动画 (仅 YSM 模型时) — 哈气 onCleanup 实证停止路径:
     * cancelPassive → onCleanup → 本方法 + 清 lma_anim_mode (FULL 循环强制停)。
     */
    public static void stopRoulette(EntityMaid maid) {
        if (maid.isYsmModel()) maid.stopRouletteAnim();
    }

    // ⚠️ 以下 4 个原语未校对 (YsmWriter 迁移遗留, 当前无调用方, 待实际场景验证后再启用)

    /** ⚠️ 未校对: 禁用 YSM 模型 (恢复默认模型) */
    public static void disableModel(EntityMaid maid) {
        maid.setIsYsmModel(false);
    }

    /** ⚠️ 未校对: 重置 roaming 变量 (YSM 动画变量) */
    public static void resetRoamingVars(EntityMaid maid) {
        maid.roamingVars.clear();
        maid.roamingVarsUpdateFlag++;
    }

    /** ⚠️ 未校对: 设置 roaming 变量 (YSM 动画变量) */
    public static void setRoamingVar(EntityMaid maid, String name, float value) {
        maid.roamingVars.put(name, value);
        maid.roamingVarsUpdateFlag++;
    }

    /**
     * 设置 YSM 模型 — mode="ysm女仆模型"=随机内置预设, 否则手动输入。
     * 逐字迁移自旧 YsmWriter.setModel (语义不变)。
     */
    public static void setModel(EntityMaid maid, String mode, String modelId, String texture, String modelName) {
        var rng = ThreadLocalRandom.current();
        String mid, tex;
        Component name;
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
