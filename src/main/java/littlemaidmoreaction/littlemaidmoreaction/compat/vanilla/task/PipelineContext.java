package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task;

/** 管道上下文 — AI传入参数 + 内部生成的任务ID */
public record PipelineContext(String target, int targetCount, String taskId) {
    public static final int UNLIMITED = -1;
}
