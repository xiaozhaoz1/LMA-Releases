package littlemaidmoreaction.littlemaidmoreaction.compat;

import littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.SlashBladeCompat;
import littlemaidmoreaction.littlemaidmoreaction.compat.tpm.TpmCompat;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.VanillaCompat;
import littlemaidmoreaction.littlemaidmoreaction.compat.ysm.YsmCompat;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 兼容模块调度中心 — 参考 TLM {@code init/registry/CompatRegistry}。
 *
 * <p>在 {@link InterModEnqueueEvent} 阶段检测目标模组是否加载，
 * 若加载则调用对应 Compat 的 {@code init()} 方法完成初始化（版本检查、扫描注册等）。</p>
 *
 * <p>新增兼容模组只需在此添加一个 {@code checkModLoad} 调用即可。</p>
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompatRegistry {
    public static final String YSM = "yes_steve_model";
    public static final String SLASHBLADE = "slashblade";
    public static final String TPM = "true_power_of_maid";
    public static final String CREATE = "create";

    /** v35.5: 防止 scanAllCompatEarly() + onEnqueue() 双重初始化 */
    private static volatile boolean earlyScanned = false;

    /**
     * ★ Bug #68 fix: 提前扫描 compat 模块（mod 构造器中调用）。
     * 注册动作/条件到 Registry，确保 RuleActionStorage.load() 读取规则时
     * 所有 compat 动作已可用，消除 "未知动作类型" 警告。
     * 幂等 — 条件/动作重复注册无害，earlyScanned 守卫防止重复执行副作用。
     */
    public static void scanAllCompatEarly() {
        if (earlyScanned) return;
        earlyScanned = true;
        doScan();
    }

    @SubscribeEvent
    public static void onEnqueue(final InterModEnqueueEvent event) {
        event.enqueueWork(() -> {
            // ★ v35.5: 若 scanAllCompatEarly() 已扫描则跳过，避免双重初始化
            if (earlyScanned) return;
            earlyScanned = true;
            doScan();
        });
    }

    /** 执行 compat 扫描 (条件/动作注册 + 门控检查) */
    private static void doScan() {
        littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.FlowTaskCompat.init();
        VanillaCompat.init();
        checkModLoad(YSM, YsmCompat::init);
        checkModLoad(SLASHBLADE, SlashBladeCompat::init);
        checkModLoad(TPM, TpmCompat::init);
    }

    private static void checkModLoad(String modId, Runnable runnable) {
        if (ModList.get().isLoaded(modId)) {
            runnable.run();
        }
    }

    // ─── compat 类型查询（供 GUI 保存时自动检测）───────────────

    /** 已知 compat 条件 key → modId 映射 */
    private static final Map<String, String> COMPAT_CONDITION_KEYS = Map.ofEntries(
        Map.entry("is_ysm_model", "ysm"),
        Map.entry("ysm_model_id", "ysm"),
        Map.entry("ysm_model_texture", "ysm"),
        Map.entry("ysm_model_name", "ysm"),
        Map.entry("is_roulette_playing", "ysm"),
        Map.entry("roulette_anim", "ysm"),
        Map.entry("ysm_has_roaming_var", "ysm"),
        Map.entry("ysm_roaming_var", "ysm"),
        Map.entry("ysm_roulette_dirty", "ysm")
    );

    /** 已知 compat 动作 type → modId 映射 */
    private static final Map<String, String> COMPAT_ACTION_TYPES = Map.ofEntries(
        Map.entry("set_ysm_model", "ysm"),
        Map.entry("play_ysm_roulette", "ysm"),
        Map.entry("stop_ysm_roulette", "ysm"),
        Map.entry("disable_ysm_model", "ysm"),
        Map.entry("set_ysm_roaming_var", "ysm"),
        Map.entry("reset_ysm_roaming_vars", "ysm")
    );

    /** 查询条件 key 所属 compat 模组 */
    public static String getModByConditionKey(String key) {
        return COMPAT_CONDITION_KEYS.get(key);
    }

    /** 查询动作 type 所属 compat 模组 */
    public static String getModByActionType(String type) {
        return COMPAT_ACTION_TYPES.get(type);
    }

    /** 扫描条件/动作列表，推断依赖的 compat 模组 */
    public static List<String> detectCompat(
            List<littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef> conditions,
            List<littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep> actions) {
        LinkedHashSet<String> mods = new LinkedHashSet<>();
        for (var c : conditions) {
            String mod = getModByConditionKey(c.key());
            if (mod != null) mods.add(mod);
        }
        for (var a : actions) {
            String mod = getModByActionType(a.typeId());
            if (mod != null) mods.add(mod);
        }
        return List.copyOf(mods);
    }
}
