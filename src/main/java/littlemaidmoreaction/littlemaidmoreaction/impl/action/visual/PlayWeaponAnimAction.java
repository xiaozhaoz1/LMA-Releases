package littlemaidmoreaction.littlemaidmoreaction.impl.action.visual;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 按武器匹配动画 — INSTANT 模式。
 *
 * <p>GUI 中为每种武器类型配置动画名（逗号分隔多选随机），
 * 运行时根据女仆手持物品匹配武器类型，选取对应动画并委托给 {@link PlayAnimAction}。</p>
 *
 * <p>参数存储格式：{@code wpn_sword = "execution, slash"}, {@code wpn_axe = "chop"} 等。
 * 武器类型匹配使用 {@code path.contains(keyword)} 模糊匹配。</p>
 */
@RuleAction
public final class PlayWeaponAnimAction implements IAction {

    /** 支持匹配的武器类型关键字（CycleButton 循环顺序） */
    public static final List<String> WEAPON_TYPES = List.of(
        "sword", "axe", "pickaxe", "shovel", "hoe",
        "trident", "bow", "crossbow"
    );

    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.SelectParam("mode", "动画模式", "INSTANT", List.of("INSTANT"))
    );

    @Override public String id() { return "play_weapon_anim"; }
    @Override public String displayName() { return "播放武器动画"; }
    @Override public ActionCategory category() { return ActionCategory.VISUAL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> params) {
        Item item = ctx.maid().getMainHandItem().getItem();
        String path = ForgeRegistries.ITEMS.getKey(item).getPath().toLowerCase(Locale.ROOT);

        // 匹配武器类型关键字
        String matched = null;
        for (String type : WEAPON_TYPES) {
            if (path.contains(type)) { matched = type; break; }
        }
        if (matched == null) matched = "default";

        // 读取该武器类型的动画配置
        String animNames = params.getOrDefault("wpn_" + matched,
            params.getOrDefault("wpn_default", "execution"));
        if (animNames.isEmpty()) animNames = "execution";

        // 委托 PlayAnimAction
        Map<String, String> delegated = new LinkedHashMap<>();
        delegated.put("mode", "INSTANT");
        delegated.put("anim", animNames);
        new PlayAnimAction().execute(ctx, delegated);

        LittleMaidMoreAction.LOGGER.info("[LMA/PlayWeaponAnim] maid={} item={} matched={} anims={}",
            ctx.maid().getId(), path, matched, animNames);
    }
}
