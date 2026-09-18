package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

/**
 * **当前交付目标** (客户端态, v79.63 用户裁定 (B): 容器菜单只列"女仆当前任务需要的"角色)。
 *
 * <p>玩家手持标记/绑定工具右键女仆时由平台客户端入口写入 (见
 * {@code LmaForgeClientEntry}/{@code LmaNeoForgeClientEntry} 的 EntityInteract 监听);
 * 之后木棍右键容器时, {@link FarmContainerScreen} 据此过滤角色按钮。
 *
 * <p>为空 = 未知 ⇒ 菜单回退显示全部 4 角色 (不阻塞老习惯)。
 */
public final class MarkTarget {

    private static String taskType = "";

    private MarkTarget() {}

    /** 记录"当前要交付的女仆"的任务类型 (客户端右键女仆时调用) */
    public static void set(String task) {
        taskType = task == null ? "" : task;
    }

    public static String get() {
        return taskType;
    }

    /** 清空 (尚未使用, 预留给"取消交付"手势) */
    public static void clear() {
        taskType = "";
    }
}
