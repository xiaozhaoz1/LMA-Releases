package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.EntitySearch;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

/**
 * 实体交互动作抽象基类 (v12 P2)。
 *
 * <p>自动处理实体搜索+排序，子类只需覆写 matchEntity + interact。
 * 使用 {@link EntitySearch} 搜索原语。
 *
 * <h3>快速创建</h3>
 * <pre>{@code
 * @RuleAction
 * public class HealVillagerAction extends AbstractEntityInteraction {
 *     @Override public String id() { return "heal_villager"; }
 *
 *     @Override
 *     protected boolean matchEntity(Entity entity, EntityMaid maid) {
 *         return entity instanceof Villager v && v.isAlive() && v.getHealth() < v.getMaxHealth();
 *     }
 *
 *     @Override
 *     protected void interact(Entity entity, EntityMaid maid, Map<String,String> params) {
 *         ((LivingEntity) entity).heal(10);
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractEntityInteraction implements IAction {
    private static final List<TypedParam<?>> BASE_PARAMS = List.of(
        new TypedParam.IntParam("range", "搜索范围", 10),
        new TypedParam.IntParam("vertical", "垂直范围", 4),
        new TypedParam.IntParam("max", "最大交互数", 1)
    );

    @Override
    public List<TypedParam<?>> params() { return BASE_PARAMS; }

    @Override
    public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return;

        int range = parseInt(rawParams.get("range"), 10);
        int vertical = parseInt(rawParams.get("vertical"), 4);
        int max = parseInt(rawParams.get("max"), 1);

        // ★ 使用 EntitySearch 原语 (v12 P1)
        List<EntitySearch.Match> matches = EntitySearch.findEntities(
            maid.level(), maid.blockPosition(), range, vertical,
            e -> matchEntity(e, maid)
        );

        int count = 0;
        for (EntitySearch.Match match : matches) {
            if (count >= max) break;
            interact(match.entity(), maid, rawParams);
            count++;
        }
    }

    /** 实体匹配条件。 */
    protected abstract boolean matchEntity(Entity entity, EntityMaid maid);

    /**
     * 对匹配的实体执行交互。
     * 每个 match 调用一次，按距离由近到远。
     */
    protected abstract void interact(Entity entity, EntityMaid maid, Map<String, String> params);

    protected static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
