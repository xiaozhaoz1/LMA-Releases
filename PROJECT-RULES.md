# LMA-MAIN 项目守则 (Project Rules)

> **这份文件约束"我怎么写代码"** —— 目标读者是 AI 协作者，也适用于人类贡献者。
> 建立方式: ① 学习各大开源项目的公开实践（见 §7 引用）② 汇总本项目**已积累的铁律与错题**
> （`AGENTS.md` 十条实证铁律 + `docs/lessons-learned.md` 编号错题 + `docs/AI-CODING-GUIDE.md` §1 分层判据）。
> **冲突时优先级**: 用户当轮指令 > 本守则 > 我已形成的习惯 ✗（习惯不得凌驾于守则）。

---

## 1. 动手前（Pre-flight）

| # | 规则 | 依据 |
|---|---|---|
1.1 | **先读目标文件所在包的 `README.md`**（含「已知陷阱」与「连接链」）; 改**共享面**（`task/api` 接口 · `WorkstationPipeline.gate` · `MaidData.cfg/cfgOrCreate` · `config/*` · 网络包 · 事件 handler · `TaskRegistryManifest`）还要读**全部调用方/实现方** README 并**列影响面清单** | 铁律 #0（2026-09-13 用户裁定; 两次实机事故来源）|
1.2 | **判据必须查"权威源"，不是查记忆** —— 分层/归位问题查 `docs/AI-CODING-GUIDE.md §1`; 是否踩过坑查 `docs/lessons-learned.md`; 外部 API/版本差异**联网或读源码**核实 | 用户裁定"铁律交叉确认应成为标准动作"; `fact-forcing` 铁律 |
1.3 | **方案先过目再动手**（除用户明确授权自主改）; 不确定就问，不猜 | AGENTS 铁律 1 |
1.4 | **影响面 grep 不得排除被查层自身** ✗ —— 要排除的是"**定义**"，不是"整个包"（否则会漏掉唯一调用方; 本项目已踩过） | 2026-09-21 实测（`sendToTracking` 误判为死方法 ⇒ 编译红）|
1.5 | **一次只动一个变量**; 一次取证做全（静态枚举全路径 + 单点覆盖 + 跑到红即停），不做"改一点→复跑→猜" | 2026-09-21 用户裁定 |
1.6 | 探针/诊断: **只读零行为 · 失败时求值 · 记录 UUID+位置+tick+来源 · 抓到一次即停 · 用后删净**（残留 grep = 0） | AGENTS 铁律 7 |

## 2. 写代码（Write）

| # | 规则 | 依据 |
|---|---|---|
2.1 | **分层判据 = 唯一标准**（§1）: `task → vanilla/api/config` · `api → task/vanilla` · `compat → task/vanilla` · `vanilla → 仅 MC/TLM + api`；**任何新代码先回答"它属于哪层"**，再动手 | §1（ArchGuardTest 6 条零容忍）|
2.2 | **IO 只在 `vanilla/input|output`**: 单格读允许直读（纯透传无增益 ✗），但**同批格跨 tick 反复读必须走 `vanilla/cache/BlockPatternCache`**；方块写走 `vanilla/output/BlockWriter` ✓ | 2026-09-21 io 判定; 错题 #255 |
2.3 | **输入防御（用户 2026-09-21 要求，对齐 Cloth Config「读入即校验」）** —— 见 §3 | 用户裁定 + `[NBT-GUARD]` 落地实例 |
2.4 | **不为"零调用方"的抽象建面** ✗（契约/工具/原语先要有真实调用方才加） | 错题 #183 |
2.5 | **家族归位看形态，不看驱动** ✓（饰品成套 ⇒ `bauble/<名>/`；驱动差异用 `task/pipeline/sense/<X>Trigger` 触发口解决）| 2026-09-21 用户裁定（实例: `bauble/token` + `TokenStealTrigger`）|
2.6 | **注释会被重构删掉，README 是跨会话契约** ⇒ 改完**同步该包 README**（明细表/连接链/陷阱表）; 守护 `ReadmeDriftGuardTest`（37 条目录）| 铁律 #0 第 3–4 条 |
2.7 | **不做无谓包装** ✗（`level.getBlockState(pos)` 这类一行 MC API 包一层 = 语义增益为零）| 2026-09-21 读侧裁定 |
2.8 | **修 bug 连带扫同类** + 记入 `docs/lessons-learned.md` | AGENTS 铁律 |

## 3. 输入防御细则（Input Defense）★ 本轮新增

**原则（Parse, don't validate）**: 外部输入 = **存档 NBT/PD · 网络包 · 命令参数 · 资源/JSON · 配置文件**。
读到"形状不对"的数据时，**绝不裸用** ⇒ 一律**校验 + 兜底 + 记一次可查日志**：

| 场景 | 做法 |
|---|---|
**类型不符**（键在但不是 Compound 等）| **warn（`[NBT-GUARD]` 前缀，带 uuid/键/实际类型）+ 就地自愈**（覆盖成空 Compound）⇒ **不静默失败** ✗（MC `getCompound` 对坏类型会**静默**返回临时空 tag ⇒ 上层"配置全空"且看不出原因 ✗；写路径更会**静默丢写** ✗ —— 本项目 2026-09-21 在 `MaidData.cfg/cfgOrCreate` 修复此变体 ✓）|
**解析型读取**（`ResourceLocation` / `ItemStack` / `BlockState` / Codec 解码）| 用 `tryParse` 形式或包 try-catch ⇒ **失败返回空/默认 + warn**（不用裸 `new ResourceLocation(...)` ✗）|
**网络 C2S 解码**（信任边界）| `readNbt/readUtf` 等**必须判空/校验**（错题 #194: 空 NBT 直传 handler = 崩服）|
**命令参数** | 越界/空值走显式 `fail` 消息（玩家可读），不靠异常栈 |
**资源/JSON 加载** | 单条失败**不阻断整体**（warn + 跳过该条），启动期补齐汇总日志 |
**数值越界** | 读到后**夹取（clamp）**到合法区间 + warn（不要相信存档里的值 ✓）|
**日志可查性** | 所有防御分支用统一前缀（`[NBT-GUARD]` / `[CFG-GUARD]` / `[NET-GUARD]` / `[FILE-GUARD]` / 模块名）⇒ **一条 grep 定位全部脏数据事故** ✓ |

### §3.2 已完成面审计（2026-09-21 实测 —— 回答"防御做完了吗"）

| 面 | 站点数 | 现状 |
|---|---|---|
**文件/资源读取**（`Files.read*` / `list` / `walk` / `newInputStream` / `newBufferedReader` / `getResourceAsStream` / `readString` …）| **40** | ✅ **全部有防护**：在 `try` 内，或调用方双层 catch（例 `CompatToggle.loadFrom` 声明 `throws IOException`，调用方 `load()` 有 `catch(IOException)` + `catch(Throwable)` ✓）；`FestivalLoader` 空判 + try ✓；`YsmAnimInjector` 的 `Files.walk` 迭代**已按上次 `NoSuchFileException` 事故加固** ✓ |
**文件写入/删除**（`write*` / `createDirectories` / `move` / `copy` / `delete*` / `mkdirs`）| **16** | ✅ **全部有防护**（同款：在 try 内或调用方 catch ✓）|
**网络解码**（`decode(FriendlyByteBuf)` × 20）| 20 | ✅ **3 处 `readNbt` 已全判空**：`TaskConfigActionPacket`（**C2S 信任边界**，错题 #194 已修 ✓）· `ReplyTaskConfigPacket` · `LmaAnimSyncMessage`（S2C，**2026-09-21 补判空** `[NET-GUARD]` ✓ —— 跨版本载荷形状不符会 NPE ✗）|
**per-task 配置**（存档 NBT 唯一入口）| 1 个杠杆点 | ✅ 已加固（`MaidData.cfg/cfgOrCreate`，commit `aee7ac9` ✓ `[NBT-GUARD]`）|
**解析型读取**（`ItemStack.of` / Codec）| 2 | ✅ 已量：两版都**优雅降级** ⇒ **不加包装** ✓（§3.1）|

> **枚举完整性**: 读侧用 8 词表 + 宽词表（`FileOutputStream`/`RandomAccessFile`/`ZipFile`/`Properties`/`Scanner`/`URLConnection`…）
> 双扫，无新增站点 ✓ ⇒ 上表即当前完整面 ✓。**新增 IO/网络/存档读点时，按 §3 设防并更新本表** ✓。

### §3.1 设防判据 ★（**先量后防** —— 不是所有读取都要 try-catch ✗）

设防前必须回答三个问题，**三个都"是"才加防御** ✓：

| 问题 | 否 ⇒ 结果 |
|---|---|
① 这段读取**会抛异常**吗？（`new ResourceLocation(...)` / Codec 解码 / 数组索引 / 除零 ✓）| 不会抛 ⇒ **不包 try-catch** ✗ |
② 会**静默错**吗？（MC 的 `getCompound`/`getInt` 对坏类型**静默返回默认值** ✗ —— "不崩"≠"安全" ✓）| 不会 ⇒ 不加 ✓ |
③ 这个位置是**杠杆点**吗？（唯一入口 > 逐点 ★）| 不是 ⇒ 先找杠杆点 ✓ |

**已量过、结论 = 不设防的实证**（LMA 2026-09-21）：
- `ItemStack.of(CompoundTag)`：1.20.1 = `ItemStack.java:182`（内部 `resultOrPartial` 容错 ✓）；1.21.1 = Codec 路径
  `xmap(...orElse(ItemStack.EMPTY))` ⇒ **两版都优雅降级为 EMPTY + 日志** ✓ ⇒ 2 处动态解析点**不加包装** ✓
  （背景: 1.21 起物品 NBT 改为 **Components** 体系 — [变更说明](https://feedback.minecraft.net/hc/en-us/community/posts/24488228743565-Let-s-talk-about-Feature-Item-Stack-Components)）
- MC 的 `CompoundTag.getCompound/getInt/getString` 对**类型不符**一律返回默认值（不抛 ✓）⇒ 真正危险的是
  **"静默错"**（本项目 `cfgOrCreate` 因此静默丢写 ✗ ⇒ 已修 ✓）与**解析型 API**（`ResourceLocation` 等 ✓）。

> ❌ **反面**: 把 558 处 NBT 读全包 try-catch ✗（噪音 + 性能 + 掩盖真 bug）; 只在**会抛异常/会静默错**的
> "解析点与信任边界"设防 ✓（本项目的杠杆点 = `MaidData.cfg/cfgOrCreate` 一个入口覆盖全部 per-task 配置 ✓）。

## 4. 验证与提交（Verify & Commit）

| # | 规则 |
|---|---|
4.1 | **验证门 = 双平台编译 + 单测 566/0 + gametest 121 × 双平台**（红则先归因，不当 flake 放过 ✗）|
4.2 | **新增校验法先自测**: 用**已知好样本 + 已知坏样本**各测一次（例: 塞假类给 README 守护 ⇒ 必须红并点名）|
4.3 | **批次纪律**: 一次一个逻辑变更（primitive → 迁移调用 → 验证 → **独立提交**）; 提交 = **回滚点**; 不混多个逻辑进同一提交（Google「小 CL」）|
4.4 | **数字对账**: 改完核基线表（类数/用例数/规则数/守护条数）; 表是**唯一数字真相源** |
4.5 | **提交信息**写清 **根因 + 修法 + 验证**（不写"用户反馈/建议"）; 不含隐私/构建产物 |
4.6 | 版本号改动须同步 5 处; 发版走 `deploy-lma.sh` 闸门（用户手动上传 Release ✓）|
4.7 | **README/守则类改动**：本守则与项目 README 的措辞改动**先交用户校对** ✓ |

## 5. 事故与归档（Incident Discipline）

- **红即停**: 抓到失败样本就**停止**（不在红状态下继续叠改动 ✗），先取证归因。
- **"间歇"要先证伪**: 判定 flake 前必须做**翻转举证**（例: `neo 3/3 红 → forge 红` 的翻转 ⇒ **槽位敏感真缺陷**，不是"偶发" ✗）。
- **归因边界要写清**: 已证到哪一层、哪一段**未逐帧证** ✗（例: "已证到 `SchedulePos`/半径层; 具体触发链未逐帧证"）。
- **每类事故归档**: `build-logs/VERDICT-*.md` / `PLAN-*.md` / `LIST-*.md` + 错题集条目编号。

## 6. 与用户协作（Collaboration）

- 30 秒级进度心跳; 不长时间静默。
- 用户凭经验的判断命中率高 ⇒ **把用户假设当"待验证"而非"待解释"**（去查、别去辩）。
- **错了要自己说**（本会话多次: 判据套错/`grep` 漏调用方/"死方法"误判 ⇒ 均当场更正并留档 ✓）。
- 用户给定结论后**按用户版本执行**（如"驱动改被动管道"✓）。

## 7. 引用来源（本守则的外部参考）

- Google Engineering Practices — [Small CLs](https://google.github.io/eng-practices/review/developer/small-cls.html) · [Code Review Standards](https://google.github.io/eng-practices/)（→ §4.3 小步提交/独立回滚点）
- "Parse, don't validate" — [Miggo 实践文](https://medium.com/@miggo-engineering/parse-dont-validate-in-practice-4b1a10177759)（→ §3 输入防御原则）
- Cloth Config / Konfig / Configured — [clothconfig.com](https://clothconfig.com/) · [Konfig](https://modrinth.com/mod/konfig) · [Configured](https://github.com/MrCrayfish/Configured)（→ §3 "读入即校验 + 不静默失败"）
- Minecraft/NeoForge 官方文档与源码（→ `fact-forcing`: 版本差异一律查证; 例 [LevelWriter 1.21.1](https://lexxie.dev/neoforge/1.21.1/net/minecraft/world/level/LevelWriter.html) 的 flag 语义）
- 本项目: `AGENTS.md`（十条实证铁律）· `docs/AI-CODING-GUIDE.md §1`（分层判据）· `docs/lessons-learned.md`（编号错题）· `docs/ARCHITECTURE.md` 顶部（基线表）
