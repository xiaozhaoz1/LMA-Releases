package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ActionStep} 可变构建器 — 替代旧 ActRow (v10)。
 *
 * <p>编辑器使用构建器编辑动作参数，保存时调用 {@link #build()} 生成不可变记录。</p>
 */
final class ActionStepBuilder {
    String type;
    Map<String, String> params;

    ActionStepBuilder(ActionStep step) {
        this.type = step.typeId();
        this.params = new LinkedHashMap<>(step.params());
    }

    /** 仅指定类型，无参数 */
    ActionStepBuilder(String type) {
        this.type = type;
        this.params = new LinkedHashMap<>();
    }

        /** 从旧 ActRow 式参数创建 (迁移兼容) */
    static ActionStepBuilder fromParts(String type, Map<String, String> params) {
        return new ActionStepBuilder(new ActionStep(type, params != null ? params : Map.of()));
    }

    /** 深拷贝 */
    ActionStepBuilder copy() {
        return new ActionStepBuilder(build());
    }

    IAction action() { return ActionRegistry.get(type); }

    ActionStep build() {
        return new ActionStep(type, Map.copyOf(params));
    }

    String label() {
        var a = action();
        String name = a != null ? a.displayName() : type;
        if (params.isEmpty()) return name;
        var sb = new StringBuilder(" (");
        int n = 0;
        for (var e : params.entrySet()) {
            String v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            if (n++ > 0) sb.append(", ");
            if (n <= 4) sb.append(paramDisplayName(e.getKey())).append("=").append(v);
        }
        if (n > 4) sb.append(", ...");
        if (n == 0) return name;
        sb.append(")");
        return name + sb;
    }

    void resetTo(IAction action) {
        type = action.id(); params.clear();
        action.params().forEach(p -> params.put(p.name(), String.valueOf(p.defaultValue())));
    }

    /** 参数键中文显示名映射 — 覆盖全部动作类型的参数键 */
    static String paramDisplayName(String key) {
        return switch (key) {
            case "anim_name"      -> "动画名";
            case "priority"       -> "优先级";
            case "particle_id"    -> "粒子ID";
            case "delta_x"        -> "扩散X";
            case "delta_y"        -> "扩散Y";
            case "delta_z"        -> "扩散Z";
            case "speed"          -> "速度";
            case "sound_id"       -> "音效ID";
            case "volume"         -> "音量";
            case "pitch"          -> "音调";
            case "amount"         -> "伤害量";
            case "damage_type"    -> "伤害类型";
            case "ignore_armor"   -> "无视护甲";
            case "strength"       -> "力度";
            case "vertical"       -> "垂直力度";
            case "horizontal"     -> "水平力度";
            case "ratio"          -> "吸血比例";
            case "seconds"        -> "持续秒数";
            case "projectile_id"  -> "弹射物ID";
            case "inaccuracy"     -> "散布度";
            case "effect_id"      -> "药水ID";
            case "duration"       -> "持续时间";
            case "amplifier"      -> "等级";
            case "show_particles" -> "显示粒子";
            case "multiplier"     -> "倍率";
            case "mode"           -> "动画模式";
            case "anim"           -> "动画名";
            case "anim_start"     -> "开始动画";
            case "anim_casting"   -> "施法动画";
            case "anim_end"       -> "结束动画";
            case "dur_start"      -> "开始时长的tick";
            case "dur_casting"    -> "施法时长的tick";
            case "dur_end"        -> "结束时长的tick";
            case "distance"       -> "距离";
            case "offset_x"       -> "偏移X";
            case "offset_y"       -> "偏移Y";
            case "offset_z"       -> "偏移Z";
            case "toward_target"  -> "朝向目标";
            case "additive"       -> "叠加模式";
            case "speed_mult"     -> "速度倍率";
            case "task"           -> "任务ID";
            case "sitting"        -> "坐下";
            case "enabled"        -> "启用";
            case "schedule"       -> "日程";
            case "value"          -> "数值";
            case "key"            -> "数据键";
            case "model_id"       -> "模型ID";
            case "pack_namespace" -> "包命名空间";
            case "invulnerable"   -> "无敌";
            case "command"        -> "命令";
            case "as"             -> "执行身份";
            case "at"             -> "执行位置";
            case "power"          -> "爆炸威力";
            case "destroy_blocks" -> "破坏方块";
            case "set_fire"       -> "生成火焰";
            case "cosmetic"       -> "仅视觉效果";
            case "entity_id"      -> "实体ID";
            case "nbt"            -> "NBT标签";
            case "count"          -> "数量";
            case "spread"         -> "扩散半径";
            case "item_id"        -> "物品ID";
            case "ticks"          -> "延迟tick";
            case "interval"       -> "间隔tick";
            case "condition"      -> "条件JSON";
            case "skip"           -> "跳过步数";
            case "message"        -> "消息内容";
            case "type"           -> "类型";
            case "target"         -> "目标";
            case "begging"        -> "乞讨";
            case "rideable"       -> "可骑乘";
            case "swinging"       -> "挥舞";
            case "aiming"         -> "瞄准";
            case "hand"           -> "手部";
            case "can_climb"      -> "可攀爬";
            case "x"              -> "坐标X";
            case "y"              -> "坐标Y";
            case "z"              -> "坐标Z";
            case "attribute"     -> "属性";
            case "seeds"         -> "种子列表";
            case "scope"         -> "作用范围";
            default               -> key;
        };
    }
}
