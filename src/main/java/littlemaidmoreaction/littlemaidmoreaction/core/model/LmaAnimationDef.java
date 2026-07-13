package littlemaidmoreaction.littlemaidmoreaction.core.model;

import java.util.Map;

/**
 * 动画元数据定义 — 每个动画的独立配置。
 *
 * <p>存储在 config/littlemaidmoreaction/animationsetup/<name>.json 中，
 * 每文件一个 record 的 JSON 序列化。
 *
 * <p>注意：casting_phase 不属于此处 — 它是 PlayAnimAction 的 mode 参数决定的，
 * INSTANT 模式 1 个动画名，FULL 模式 3 个动画名 (start/casting/end)。
 */
public record LmaAnimationDef(
    String name,              // 动画名（.animation.json 文件内 animations map 的 key 名）
    int priority,             // 优先级 (1 ~ 999，默认 100)
    boolean lockMovement,     // 动画期间锁定移动
    boolean freezeAI,         // 动画期间冻结 AI
    boolean interruptible,    // 是否允许更高优先级动画打断
    Map<String, String> extra // 额外扩展参数（未来兼容）
) {
    public static final int DEFAULT_PRIORITY = 100;

    /** 回退默认值 — 动画在 animationsetup/ 中无配置时使用 */
    public static LmaAnimationDef fallback(String name) {
        return new LmaAnimationDef(name, DEFAULT_PRIORITY,
                true, true, false, Map.of());
    }

    // with* 方法 — 不可变更新
    public LmaAnimationDef withName(String n)         { return new LmaAnimationDef(n, priority, lockMovement, freezeAI, interruptible, extra); }
    public LmaAnimationDef withPriority(int v)          { return new LmaAnimationDef(name, v, lockMovement, freezeAI, interruptible, extra); }
    public LmaAnimationDef withLockMove(boolean v)      { return new LmaAnimationDef(name, priority, v, freezeAI, interruptible, extra); }
    public LmaAnimationDef withFreezeAI(boolean v)      { return new LmaAnimationDef(name, priority, lockMovement, v, interruptible, extra); }
    public LmaAnimationDef withInterruptible(boolean v) { return new LmaAnimationDef(name, priority, lockMovement, freezeAI, v, extra); }
}
