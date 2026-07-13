package littlemaidmoreaction.littlemaidmoreaction.core.spi.action;

/**
 * 动作分类枚举 — 替代 v4 的 String category() 弱类型设计。
 *
 * <p>在编译期约束动作的分类归属，编辑器可据此进行分组展示和过滤。
 * 扩展新动作时选择最贴合的类别即可。</p>
 */
public enum ActionCategory {
    /** 战斗相关：伤害、治疗、击退、护盾、火焰等 */
    COMBAT,
    /** 移动相关：传送、寻路、速度修改等 */
    MOVEMENT,
    /** 视觉相关：粒子效果、动画、标题显示等 */
    VISUAL,
    /** 状态效果：药水效果、清除效果等 */
    EFFECT,
    /** 女仆核心操作：状态、好感度、数据相关 */
    MAID,
    /** 女仆扩展操作：通过 API/扩展注册的额外女仆操作 */
    MAID_EXT,
    /** 世界交互：放置方块、掉落物、爆炸等 */
    WORLD,
    /** 物品操作：给予、丢弃、装备等 */
    ITEM,
    /** 流程控制：条件判断、循环、等待、取消事件等 */
    CONTROL,
    /** 消息通信：发送聊天消息、状态反馈等 */
    MESSAGE
}
