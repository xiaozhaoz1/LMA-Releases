# AGENTS.md (LMA-MAIN)

> 本文件是仓库内执行纪律入口。主纪律文件在仓库外: `../AGENTS.md` + `../CLAUDE.md` (与本仓库同盘相邻)。

## 新会话开工 (任何预设, 任何模式)

1. 先读 `../CLAUDE.md` 顶部「会话交接」段 — 最新状态/待办在这里。
2. 读 `../multiprompts/output/HANDOFF.md` — 唯一事实源: 任务表/契约冻结/交接队列/批次记录/完成门。
3. 读 `docs\ARCHITECTURE.md` (必读) + 相关包 README; 错题集 `docs\lessons-learned.md` (编号与条数以该文件头行为准)。

## 铁律 (摘录, 全文见外部 AGENTS/CLAUDE)

- **修改前先计划并获用户批准**; 不确定就问, 不猜。
- **fact-forcing**: 标识符/键名/外部 API 一律读源码验证, 禁止凭记忆/文档猜。
- **改代码前必读该包 README** (铁律 #0 — 2026-09-13 用户裁定, 全文见 `docs/AI-CODING-GUIDE.md` §2.9):
  1. 改任何文件**之前**, 先读**它所在包**的 `README.md` —— 重点看**已知陷阱表**与**连接链**章节, 再看源码;
  2. 改**共享面** (`task/api` 接口 · `WorkStationPipeline.gate` · `MaidData.cfg`/`cfgOrCreate` · `config/*` 键 ·
     网络包 · 事件 handler · `TaskRegistryManifest`) 还必须读**所有调用方/实现方**的 README, 并在动手前**列出影响面清单**;
  3. 改完**同步该包 README** (明细表/连接链/陷阱表); 该包无 README 先补;
  4. **注释会被重构删掉, README 是跨会话契约**。
  > 依据: 本会话两次实机事故 (工作站任务不走 / per-task 配置改了不生效) 都源于"没读全就改";
  > 守护: `ReadmeDriftGuardTest` (新增类忘写 README、README 缺陷阱章节 = **测试红**)。
- 修 bug 连带扫描同类问题 + 记入 `docs\lessons-learned.md`; 不用 Python 写文件。

## 验证命令 (编译必带 --no-build-cache; 测试必带 --rerun-tasks)

```bash
./gradlew :forge:1.20.1:compileJava :neoforge:1.21.1:compileJava --no-build-cache
./gradlew :forge:1.20.1:test --rerun-tasks                    # 单测 (类/用例数见 ARCHITECTURE 基线表)
./gradlew :forge:1.20.1:runGameTestServer                     # gametest (跑前清 run/gametest/world)
./gradlew :neoforge:1.21.1:runGameTestServer                  # gametest
./gradlew :forge:1.20.1:jar :neoforge:1.21.1:jar -x javadoc --no-build-cache
# neoforge 资源改动后: ./gradlew :neoforge:1.21.1:copyResourcesToClasses --rerun-tasks
# 打包后必须 unzip 验 jar 内资源/类, 不能只信 BUILD SUCCESSFUL
# 日志落 build-logs/ (编译/单测/gametest 各一份)
```

## 当前版本

**v79.72 / 0.9.72** (2026-09-17 校对; **基线表 = 唯一数字真相源**, 本文件不再抄写任何计数数字)。
