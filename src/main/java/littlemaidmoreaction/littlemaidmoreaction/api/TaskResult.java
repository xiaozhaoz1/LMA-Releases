package littlemaidmoreaction.littlemaidmoreaction.api;

/** 任务执行结果 — Brain 根据此枚举决定 complete/continue/fail */
public enum TaskResult {
    SUCCESS,   // 工作完成 → completeTask
    CONTINUE,  // 进行中 → 不调 complete/fail (如播放唱片)
    FAILED     // 失败 → failTask(reason)
}
