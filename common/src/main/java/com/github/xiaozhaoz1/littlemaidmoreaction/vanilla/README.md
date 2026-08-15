# vanilla — 原版能力层 (总览)

**作用**: 与 MC 原版世界交互的全部能力, 按两轴定级分三块:
- [execute](execute/README.md) — 世界操作协调 (跨 tick 有状态) + 采集域执行器族
- [input](input/README.md) — io 读原语 (单次查询, 通用无语义)
- [output](output/README.md) — io 写原语 (单次写入, 通用无语义)
- [fakeplayer](fakeplayer/README.md) — 假人交互 (放置/点击, io 级)

**依赖方向**: 只依赖 MC + TLM 实体 + task/service (业务调用方向: task → vanilla, 不反向)。

**修改注意**: 两轴判据 (跨 tick? 业务语义?) 见各子 README; 本层禁止 import 第三方 mod (兼容代码去 compat)。
