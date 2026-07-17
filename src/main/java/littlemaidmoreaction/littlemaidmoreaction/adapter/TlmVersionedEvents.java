package littlemaidmoreaction.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.TlmVersion;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.function.Consumer;

/**
 * TLM 版本门控事件注册。
 *
 * <p>当 TLM >= 1.5.1 时通过反射注册 {@code MaidRequestItemEvent} 处理器。
 * 此事件类仅在较新 TLM 中存在，无法在编译期引用。</p>
 */
public final class TlmVersionedEvents {

    private static volatile boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        if (TlmVersion.isV151()) {
            try {
                Class<?> eventClass = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.api.event.MaidRequestItemEvent");
                if (!net.minecraftforge.eventbus.api.Event.class.isAssignableFrom(eventClass)) {
                    LittleMaidMoreAction.LOGGER.warn("[TlmAdapter] {} is not an Event subtype, skipping", eventClass.getName());
                    return;
                }
                @SuppressWarnings("unchecked")
                Consumer<net.minecraftforge.eventbus.api.Event> listener = event -> {
                    if (eventClass.isInstance(event)) {
                        try {
                            EntityMaid maid = (EntityMaid) eventClass.getMethod("getMaid").invoke(event);
                            RuleEngine.handleEvent("maid_request_item", new RuleContext(maid));
                        } catch (Exception ex) {
                            LittleMaidMoreAction.LOGGER.error("[TlmAdapter] maid_request_item handler failed", ex);
                        }
                    }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, listener);
                LittleMaidMoreAction.LOGGER.info("[TlmAdapter] TLM >= 1.5.1: maid_request_item enabled");
            } catch (ClassNotFoundException e) {
                LittleMaidMoreAction.LOGGER.info("[TlmAdapter] TLM < 1.5.1: maid_request_item unavailable");
            } catch (Exception e) {
                LittleMaidMoreAction.LOGGER.error("[TlmAdapter] maid_request_item register failed", e);
            }
        } else {
            LittleMaidMoreAction.LOGGER.info("[TlmAdapter] TLM < 1.5.1: maid_request_item skipped");
        }
    }

    private TlmVersionedEvents() {}
}
