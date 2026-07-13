package littlemaidmoreaction.littlemaidmoreaction.adapter.tlm;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import net.mrqx.slashblade.maidpower.event.api.MaidProgressComboEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * TPM 事件 → RuleEngine 桥接。
 *
 * <h3>MaidProgressComboEvent</h3>
 * <p>TPM 在女仆尝试推进 SlashBlade 连段时触发此事件（{@code @Cancelable}）。
 * 事件包含 {@code current} (当前 combo) 和 {@code next} (下一 combo) 的 ResourceLocation。
 * LMA 可通过 {@code cancel_event} 动作阻止特定连段推进。</p>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 拦截特定 combo: maid_combo_progress + katana_combo :=: slashblade:combo_b
 *   → cancel_event → play_sound(...) → 自定义动作
 * </pre>
 *
 * <h3>注册时机</h3>
 * <p>在 {@code LittleMaidMoreAction} 构造器中调用 {@link #register()}，
 * TPM 未安装时 MaidProgressComboEvent 类不可用，但此文件不会被加载
 * （因为 TPM 的 CompatRegistry 门控阻止了相关类的访问）。</p>
 */
public final class TpmEventHandler {

    public static void register() {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("true_power_of_maid")) return;
        MinecraftForge.EVENT_BUS.register(new TpmEventHandler());
    }

    @SubscribeEvent
    public void onMaidComboProgress(MaidProgressComboEvent e) {
        RuleEngine.handleEvent("maid_combo_progress", new RuleContext(e.getMaid()));
    }
}
