package littlemaidmoreaction.littlemaidmoreaction.compat.ai.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.ai.scanner.NearbyBlockScanner;

/**
 * 周围功能方块上下文 — 向 LLM 报告附近的功能方块 (v10)。
 *
 * <p>注册为 tool context（promptContext=false），LLM 通过
 * {@code query_game_context("nearby_blocks")} 按需查询。
 * 默认扫描 32 格，最大 100 格。
 */
public final class LmaBlocksContext {

    public static final String CATEGORY = "nearby_blocks";
    private static final String SUMMARY =
        "Nearby functional blocks: storage, crafting stations, altars, enchanting tables, etc. " +
        "Grouped by type with distance and count.";

    private LmaBlocksContext() {}

    public static void registerAll(GameContextRegister register) {
        register.registerCategory(CATEGORY, SUMMARY, false);
        register.registerContext(CATEGORY, new NearbyBlocksItem());
    }

    private static final class NearbyBlocksItem extends AbstractMaidContext {
        private NearbyBlocksItem() {
            super("blocks_around", "Functional blocks within range");
        }

        @Override
        public String getValue(EntityMaid maid) {
            var result = NearbyBlockScanner.scan(maid,
                NearbyBlockScanner.DEFAULT_RANGE,
                NearbyBlockScanner.DEFAULT_VERTICAL);
            return result.toContextString();
        }
    }
}
