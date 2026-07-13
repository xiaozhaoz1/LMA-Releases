package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设置女仆模型 — 支持指定模型 / 全局随机 / 模型包内随机。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code mode} — "specific"（指定模型）/ "random_all"（全局随机）/ "random_pack"（包内随机）</li>
 *   <li>{@code model_id} — mode=specific 时的目标模型 ID（如 "touhou_little_maid:reimu"）</li>
 *   <li>{@code pack_namespace} — mode=random_pack 时的命名空间过滤（如 "touhou_little_maid"）</li>
 * </ul>
 *
 * <p>模型 ID 来源：{@link ServerCustomPackLoader#SERVER_MAID_MODELS#getModelIdSet()}。
 */
@RuleAction
public final class SetModelAction implements IAction {

    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.SelectParam("mode", "选择模式", "specific",
                    List.of("specific", "random_all", "random_pack")),
            new TypedParam.StringParam("model_id", "模型ID", ""),
            new TypedParam.StringParam("pack_namespace", "包命名空间", "touhou_little_maid")
    );

    @Override public String id() { return "set_model"; }
    @Override public String displayName() { return "切换模型"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> params) {
        String mode = params.getOrDefault("mode", "specific");

        switch (mode) {
            case "specific" -> {
                String modelId = params.getOrDefault("model_id", "");
                if (!modelId.isEmpty()) {
                    ctx.maid().setModelId(modelId);
                }
            }
            case "random_all" -> {
                String randomId = pickRandomFromAll();
                if (randomId != null) {
                    ctx.maid().setModelId(randomId);
                }
            }
            case "random_pack" -> {
                String namespace = params.getOrDefault("pack_namespace", "touhou_little_maid");
                String randomId = pickRandomFromPack(namespace);
                if (randomId != null) {
                    ctx.maid().setModelId(randomId);
                }
            }
        }
    }

    /** 从全部已加载模型中随机选取一个。 */
    private static String pickRandomFromAll() {
        Set<String> ids = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet();
        if (ids.isEmpty()) {
            LittleMaidMoreAction.LOGGER.warn("[SetModel] 无可用模型（模型包尚未加载？）");
            return null;
        }
        return ids.stream()
                .skip((int) (Math.random() * ids.size()))
                .findFirst()
                .orElse(null);
    }

    /** 从指定命名空间的模型中随机选取一个。过滤无匹配时降级为全局随机。 */
    private static String pickRandomFromPack(String namespace) {
        Set<String> ids = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet();
        if (ids.isEmpty()) {
            LittleMaidMoreAction.LOGGER.warn("[SetModel] 无可用模型（模型包尚未加载？）");
            return null;
        }
        List<String> filtered = ids.stream()
                .filter(id -> id.startsWith(namespace + ":"))
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            LittleMaidMoreAction.LOGGER.warn("[SetModel] 命名空间 '{}' 无匹配模型，降级为全局随机", namespace);
            return pickRandomFromAll();
        }
        return filtered.get((int) (Math.random() * filtered.size()));
    }
}
