package littlemaidmoreaction.littlemaidmoreaction.core.model;

/**
 * 条件匹配模式。
 * <p>
 * 定义规则中条件列表的匹配逻辑，决定多条条件是全部满足还是满足其一即可触发动作。
 * </p>
 */
public enum MatchMode {
    /** 所有条件必须同时满足（AND 逻辑） */
    ALL,
    /** 任意一条条件满足即可触发（OR 逻辑） */
    ANY
}
