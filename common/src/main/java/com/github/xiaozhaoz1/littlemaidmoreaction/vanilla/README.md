# vanilla — 原版能力层 (总览)

**作用**: 与 MC 原版世界交互的全部能力, 按两轴定级分三块:
- [execute](execute/README.md) — 世界操作协调 (跨 tick 有状态) + 采集域执行器族
- [input](input/README.md) — io 读原语 (单次查询, 通用无语义)
- [output](output/README.md) — io 写原语 (单次写入, 通用无语义)
- [fakeplayer](fakeplayer/README.md) — 假人交互 (放置/点击, io 级)

**依赖方向**: 只依赖 MC + TLM 实体 + task/service (业务调用方向: task → vanilla, 不反向)。

**修改注意**: 两轴判据 (跨 tick? 业务语义?) 见各子 README; 本层禁止 import 第三方 mod (兼容代码去 compat)。
**已裁定反向例外 (2026-08-15 数据层检查)**: ChainHarvestExecute / AnimExecute / CombatOutput 保留 task/* 依赖 (历史位置, 搬=churn); ChainScan 已归位 — task/api 配置读取收敛回 ChainHarvestExecute, **新代码不得再新增 vanilla→task/api 依赖** (task/data + task/service 仍允许)。
