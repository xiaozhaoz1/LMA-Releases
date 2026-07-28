package littlemaidmoreaction.littlemaidmoreaction.io;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 女仆交互桥接 — 订阅 TLM InteractMaidEvent, 路由到 LMA IO架构 (v64).
 *
 * <p>当玩家右击女仆时:
 * <ol>
 *   <li>触发 LMA 规则引擎 (maid_interact 事件)</li>
 *   <li>如果手持特定物品 (如任务书/规则书), 可打开对应 GUI</li>
 *   <li>扩展现有 IO 架构 (IReader/IWriter) 的右键交互能力</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class MaidInteractBridge {

    private MaidInteractBridge() {}

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) return;

        // 触发规则引擎 — 规则可通过 maid_interact 事件响应玩家右键
        RuleEngine.handleEvent("maid_interact",
            new RuleContext(maid, event.getPlayer(), null));

        // 预留: 根据手持物品触发特定 GUI
        // if (event.getStack().is(...)) { openGui(...); }
    }
}
