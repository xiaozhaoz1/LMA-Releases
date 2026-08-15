# task/api — 任务契约层

**作用**: 任务系统的「语言」——任务长什么样、怎么注册、可选能力有哪些。改这里 = 改所有任务的公共面。

**判据 (什么进这里)**:
- 接口/契约/注册面 (TaskPipeline / TaskRegistry / 规格表)
- 新能力优先走接口 default 方法 (自由组合, 不查继承树); 只有确需流程模板时才建抽象类
- 规格表 (TaskRegistryManifest) 保持零 MC 静态链 — 纯 JVM 可测 (错题 #174 铁律)

**依赖方向**: 可依赖 task/data; 禁止依赖 task/pipeline (反向倒挂)。

**代表**: TaskPipeline (11 默认方法) / TaskRegistry (重名 fail-fast) / TaskRegistryManifest (规格单) / TaskConfigurable / TaskSignalListener / BlockTargetNavigation / PassiveSignalSkeleton

**修改注意**:
1. 改接口签名 = 改 23 个任务 + 外部 mod 扩展面 (ADR-C7: steps() 永久保留)
2. 注册重名/漏注册启动即炸 — 规格表与注册同源维护
3. 能力接口化三原则: 能力走接口 default / 模板走抽象类 / 差异走构造注入
