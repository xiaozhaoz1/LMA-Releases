# REVIEW — LMA-MAIN 全面审查报告

- 日期: 2026-08-13
- 方式: PRO 模式全量只读审查 — 主会话亲自实证 + 3 个并行审查子 agent (依赖公告/安全漏洞/结构问题) + 1 个复查子 agent (严重项核对)
- 复查结论: 严重发现 属实 6 / 存疑 1 / 不成立 0 (C4 修正: Create 脚手架重复为 4 份非 5 份)
- 基线: 磁盘源码 (common main 289 / test 45 / forge 5 / neoforge 12; git 工作区自 v79.40 后未提交, 以磁盘为准)
- 结论: 0 CRITICAL; 2 HIGH 功能缺陷 + 1 崩服级 MEDIUM + 3 MEDIUM 遗留 + 1 HIGH 维护性重复 — 严重项已全部修复 (用户批准), 其余为可选优化

## 一、已修复 (审查批次 2026-08-13, 用户批准)

| # | 级别 | 文件:行号 | 问题 | 修复 |
|---|------|-----------|------|------|
| 1 | HIGH | network/TaskConfigActionPacket.java:78/107 | C2S 空 NBT payload → 服务端 NPE 崩服 (该包无 C2SThrottle, 可无限连发) | 双平台 handler 判空守卫 (decode 保持字节级 round-trip 对称, 不兜底) |
| 2 | HIGH | task/sense/StructureSense.java:282-291 | sweep 用 level.players() (单维度) 清全局 map → 多维度在线玩家被误清, 状态/预算被清 | 改全服 getServer().getPlayerList().getPlayers() |
| 3 | HIGH | task/sense/StructureSense.java:211-212 | PLAYER_LAST 节流缺 last!=0/now>=last 守卫 → 时钟回退时永久卡死 | 对齐 ThrottleMath.shouldThrottle 语义 |
| 4 | MEDIUM | StructureSense.java:110-127 | displaced 结构转正首信号 REFRESH 非 DISCOVER (4 信号契约破) | 从未气泡 (count=0,lastRemind=0) 转正发 DISCOVER (IN/NEAR 双分支) |
| 5 | MEDIUM | StructureSense.java:76/214/270 | PLAYER_CACHE 死 map (全项目 0 读取方) | 删除 |
| 6 | MEDIUM | StructureSense.java:241-274 | showTrigger 100t 节流吞同轮多结构 discover | 同轮 ≥2 结构合并一条气泡 (_multi:discover 先发, 个体信号照发供信号层) — 用户裁定"合并成一条" |
| 7 | LOW | versions/×2 gradle.properties + mods.toml ×2 | 版本漂移 0.9.50 vs changelog 0.9.57 | 统一升 0.9.57 (用户批准) |

测试: StructureSenseStateTest +6 用例 (转正 DISCOVER ×2 / 预算重置不重复 / joinMergedText ×3), 22→28 用例。

## 二、已知遗留核对 (v79.61 记忆 5 条)

全部「仍在」→ 本批次全部修复, 见上表 #2-6。

## 三、待修/可选清单 (未动, 需另行批准)

### 安全 (sec agent) — 批次 2 已全部修复 ✅ (2026-08-13)
- ✅ ConfigSyncPacket.java decode: 未知类型跳过不抛 (WARN 一次) + 条目数上界 2048 + 负值守卫
- ✅ MaidListResponsePacket / MaidCodexScreenPacket decode: readVarInt 上界 1024 + 负值守卫 → 空集合
- ✅ LmaAnimSyncMessage.handleClient: 空 NBT 判空返回
- ✅ AnimFileSyncPacket.isValidFileName: Windows 保留设备名 (CON/PRN/AUX/NUL/COM1-9/LPT1-9) + 64 长度上限 (+2 测试)
- INFO: InteractTriggerPacket.java:98 isOwnedBy 与 UUID 比较一致性 (可选统一)
- INFO: fakeplayer 以主人 UUID 交互方块 (设计取舍, 建议文档化攻击面)

### 结构 (struct agent)
- HIGH(维护性): Create 管线样板重复 — readPos ×4 / navigateTo ×4 / arrived ×4 / 3态脚手架 ×4 (Press/Mix/Crank/Power) → 抽 NavigateToBlockWorkPipeline 基类
- MEDIUM: GUI 6 屏样板 (5 空 renderBg / cx=leftPos+88 魔数 ×6 / 步进按钮组 ×2 / ACTION payload 组装 ×6)
- MEDIUM: ChainHarvestExecute god class 702 行 8 职责 (nearPass 5 层嵌套) — 拆分建议单独立项
- MEDIUM: GameTickPipelineManager.tickActive 93 行混 5 职责
- MEDIUM: MaidAssemblyPipeline.tickStrike 50 行 5 层嵌套
- ✅ 死代码 (批次 2 已清): ItemTransfer.java 全类删除 / StepType.WAIT / DataType.FLOAT+LONG_ARRAY (MaidData get/put 各删 2 case) / FLOW_STEP / FLOW_DATA (clearAll 保留字面量清理旧存档键)
- LOW: BlockInteractConfigScreen onClose 空覆写 / ItemListConfigScreen 未用 import

### 依赖 (dep agent)
- LOW: ClassGraph 4.8.168 → 4.8.192 (落后 24 个补丁版)
- LOW: JUnit 5.9.3 / Mockito 5.11.0 (仅测试期过旧)
- ✅ libs/lunar-1.7.7.jar 已删除 (构建走 Maven 坐标 jar-in-jar, changelog 历史条目保留)
- INFO: Gson CVE-2022-25647 / SnakeYAML CVE-2022-1471/41854 均已缓解 — 建议文档注明"依赖 MC 内置 Gson (2.10.1/2.11.0), 勿捆绑旧版"

### 杂项
- 坏味道初扫: TODO 3 (设计预留注释) / @SuppressWarnings 17 (合理强转) / System.out 0 / 凭据字面量 0 / 命令执行 0
- gh 通道正常 (xiaozhaoz1); web_search 无 key, CVE 核查走 gh api 全球公告库兜底

## 四、验证

双编译 --no-build-cache + 单测 --rerun-tasks (批次 1: 342 全绿; 批次 2: 346 全绿, 0 失败)。

## 五、架构评估 (2026-08-13 — 三路研究: 本地参考扫描 + 网络开源搜索 + 架构自评)

### 5.1 结论
- **底座健康**: TaskPipeline 三接口拆分 / PacketRegistry.validatePlatformNames fail-fast (全库最佳注册范式) / TaskStateMachine 泛型引擎 / GameTickPipelineManager 单一驱动 — 接口杠杆与注册 seam 已达项目最佳形态
- **欠账在局部性**: god class + 管线样板重复 + 12+ 静态缓存清理散落 + 双平台 557 行条件块 (130 文件) 靠纪律不靠校验
- **开源参考**: 不需要异质架构 — 直系亲属 (maid_useful_task / maid_agent, 与 LMA 同生态) + Create 分层即可, 与现有 TaskStateMachine/Coordinator/PacketCodecs 平滑对接

### 5.2 事实校准 (2026-08-13 实证)
| 项 | 旧口径 | 实证值 |
|---|---|---|
| 被动任务 | 8 | 7 (PassiveSenseRegistration L22-33) |
| 任务总数 | 24/23 | 23 (forge) / 22 (neoforge) |
| ChainHarvestExecute 静态 map | 6-7 张 | 1 张 STATES (MaidChainState 承载) |
| 全项目可变静态缓存 | 红线 "6 张" | 12+ 处 |
| 纯 JVM 测试 | 342 | 346 @Test / 45 类 |
| stonecutter 条件 | — | 557 行 / 130 文件 |

### 5.3 三方对照矩阵 (收敛点)
| 痛点 | 自评 | 本地参考 (行号实证) | 网络开源 (repo 实证) |
|---|---|---|---|
| 管线样板重复 | T3 导航 seam 统一 | maid_agent AbstractWorkTask.java:30-72 模板方法 | maid_agent 抽象任务基类层级 |
| god class | T1 抽纯内核 | maid_useful_task IMaid*Task 策略接口 + MemoryModuleRegistry | TLM 56 个单职责 Maid*Task + Behavior/Memory 分解 |
| 注册面无校验 | T2 fail-fast 统一 | — | Create ~40 个 All* 注册类 + content/foundation/infrastructure 分层 |
| 网络样板 | PacketDef 半成品 | nysmcompanion SetCompanionModelPayload.java:10-20 record+StreamCodec.composite | — |
| 兼容隔离 | — | nysmcompanion NetworkHandler.java:65-81 反射 Class.forName | — |

### 5.4 候选清单
| # | 候选 | 强度 |
|---|---|---|
| C1 | ChainHarvestExecute 抽纯内核 (ChainHarvestMath: 跳过集 TTL/charge/vRange/broken 判定) | Strong |
| C2 | MoveToBlockStateMachine 导航/工作模板基类 (收编 readPos/arrived/navigateTo, 6 个 create 管线) | Strong |
| C3 | 注册面 fail-fast 统一 (TaskRegistry manifest + 缓存声明式清理) | Strong |
| C4 | record 化网络层 (composite 不采用 — 双平台裁定) | ✅ 已做 (2026-08-13) |
| C5 | config 一致性守卫补全 (声明式清单不采用 — 反射守卫更强) | ✅ 已做 (2026-08-13) |
| C6 | GUI 配置屏样板基类 (LmaTaskConfigScreen 助手收敛 5 屏) | ✅ 已做 (2026-08-13) |
| C7 | TaskStep/StepType 移出核心接口 | ❌ ADR-C7 裁定保留 (破坏扩展 API) |
| C8 | task/data 四件套薄门面收敛 | ❌ ADR-C8 裁定保留 (纯 churn) |

### 5.5 C1-C3 实施方案 (用户批准后逐批执行)
### 5.5c 注册规格化 (2026-08-13, 用户裁定: 规格单就地化, 工厂类不建)
- TaskRegistryManifest 升级为 TaskSpec 规格表 (名字+构造引用单一真相): ALWAYS 8 / NUMEN 1 / CREATE 6 / CBC 1 (条件化) / PASSIVE 7
- TaskRegistry static 块与 PassiveSenseRegistration.init 改规格循环; 门控 if 与时序原样保留 (2026-08-11c 已驳统一门控, 不重提)
- 裁定依据: 全任务无参 new 的现实 + ChainHarvestPipeline(Mode) 参数化先例 (变体工厂是构造注入) + 已驳先例 — 域工厂类证过度设计
- 注释约定: TaskPipeline javadoc 四段式长相 (身份/行为/配置/私有业务) + FSM 固定顺序; ChainHarvestPipeline 参数化先例标注
- 验证: 双编译 + 47 类 0 失败; 错题 #204
### 5.5d LMAT 外部注册三缺口 (2026-08-14, 用户验收「一句话注册/多预设/方法严谨」)
- 缺口: (1) 被动任务无触发门面 — TaskDispatcher.submitPassive/cancelPassive 早已实现未暴露; (2) TLM 任务栏扫描快照时序依赖 — 外部 mod 晚于 LMA 注册缺 typed task; (3) currentState javadoc 6 状态漂移 (实际 4)
- 修复: LMAT 补 submitPassive/cancelPassive 两行委托; LmaTaskTypeRegistry.onTaskRegistered 迟注册钩子 (lastManager 记录, TYPED 防重, TLM TaskManager.init ImmutableMap 冻结实证 → fail-soft WARN); LMAT javadoc 预设目录 5 项 + 命名约定 (净化撞 uid 陷阱) + 注册时机
- 验证: 双编译 + 47 类 308 用例 0 失败 (XML 逐文件核算); 错题 #205
### 5.5e LMAT.simple 函数式工厂 + FSM 分派收敛 (2026-08-14, 用户裁定 A+B)
- A: LMAT.simple 函数式工厂 — 先建后删 (用户裁定撤销: 极简糖对真实 modder 零价值, 预设价值是可抄模板非省行数); LMAT.Task 保留为简单任务模板
- B: ArmTransfer/BlockInteract 删 HANDLERS Map + StateCtx + Function 双重间接 → switch 分派 (短内联/长拆顶层方法); TaskPipeline 四段式注释补分派约定; 行为零变化
- 工厂形态裁定: 域工厂层与糖类预设均不建 — 三种最小形态 (TaskSpec 方法引用 / 构造注入 / adapter 生成) 覆盖全部需求 (TLM 56 任务零工厂实证)
- 验证: 双编译 + 47 类 308 用例 0 失败; 错题 #206
### 5.5f 五层尺收敛 (2026-08-14, 架构审计 A+B+C)
- 五层尺: pipeline(流程判定) / execute(执行器微流程) / service(业务算法) / behavior(brain 习惯) / input-output(IO 原语)
- A: FurnaceService 抽取 — 配方扫描两处重复收敛 (validateSmelt 失败文案逐字保留 + resolveSmeltIngredient + 生效名单), FurnacePipeline 177→56 行
- B: HaqiService 抽取 — 挥击/音效行为细节出管线 (TARGET_OWNER/SOUND_* 常量随迁, HaqiTrigger/gametest 引用同步)
- C: ArmTransferService 归位 itemId/findMaidItem
- 行为零变化: 逐行搬移 + 双平台条件块原样搬; 验证 双编译 + 49 类 318 用例 0 失败 + gametest 10/10 ×2; 错题 #207
### 5.5b 基站重写 (2026-08-13, 用户裁定: 能力走接口 default, 继承树变平)
- BlockTargetNavigation (task/pipeline) — 走路四件套 default; MoveToBlockStateMachine 薄壳化 (4 管线零改动); RunningBelt 静态 parseTarget 直调
- PassiveSignalSkeleton (task/api) — isLongRunning 默认 false + okSignals + bubbleTrigger; 6 被动迁移 (StructureSense/Festival/SnowShovel/TempAdapt/Haqi/TorchLight)
- GatedMaidTool (compat/ai/tool) — trigger 门控 default; 8 世界修改工具迁移 (只读 6 工具刻意保持裸 ITool)
- 参考: maid_useful_task IMaidBlockDestroyTask (default 承载算法) / TLM MaidCheckRateTask→MaidMoveToBlockTask (单关注点分层) / MaidMoveToPredicateBlockTask (构造注入) / maid_storage_manager AbstractTool
- 验证: 双编译 + 354 全绿; 错题 #203

### 5.6 ADR 式裁定 (2026-08-13 — C7/C8 证据评估后保留现状)

**ADR-C7 — TaskStep/steps() 保留在 TaskPipeline 核心接口**: 证据 — steps() 消费方含 MaidTaskContext.java:82 (任务步骤进 LLM 上下文) / LmaCommand.java:124 (用户命令输出) / TaskTreeScreen (GUI), 且是 LMAT 扩展 API 文档化契约 (外部附属模组可实现); 移出核心接口 = 破坏扩展面 + 3 类消费方 + 16 管线实现全改, 纯 churn 零功能收益。裁定: 永久保留, 不再提议拆分 (StepType.WAIT 死值已于批次 2 清理, 剩余 4 值全在用)。

**ADR-C8 — task/data 门面层 (FlowTaskData 等) 保留**: 证据 — 每层门面均有真实调用方 (v79.30 迁移兼容 + 人机工程); 收敛 = 改所有调用点, 收益仅少两层薄门面。裁定: 保留现状, 新增键走 DataKey 类型化 API 即可, 不再提议合并。

**C1 — ChainHarvestExecute 抽纯内核 (批 3a) ✅ 已完成 (2026-08-13)**
- 产出: vanilla/execute/ChainHarvestMath.java (5 纯函数: chargeTicks/durabilityCropSize/durabilityCost/vRange/scanBudget) + ChainHarvestMathTest (5 用例); ChainHarvestExecute 5 处改委托, 删 2 常量, 行为零变化
- 验证: 双编译 --no-build-cache + 46 测试类 0 失败
- 说明: 跳过集 TTL/tier 退避已在 MaidChainState (纯化+有测试), 不在本次范围; hasReachableRemaining 判定 world-bound 保留在编排层
1. 盘点纯决策块: 跳过集 TTL 过期/tier 退避 (#184)、charge 蓄力时长计算、vRange 判定、hasReachableRemaining、broken==0 失败判定
2. 新建 vanilla/execute/ChainHarvestMath.java (package-private 纯静态, 参数注入 — 错题 #174 铁律)
3. ChainHarvestExecute 改委托; 配置读取 (L116/416/427/502/603) 改入参, 消除 allowed() L570-584 层级倒挂
4. 新测试 ChainHarvestMathTest (对齐 #184/#185/#186 回归场景)
5. 验证: 双编译 --no-build-cache + 单测 --rerun-tasks; 行为零变化

**C2 — MoveToBlockStateMachine (批 3b) ✅ 已完成 (2026-08-13)**
- 产出: task/pipeline/MoveToBlockStateMachine.java (targetKey 可覆写钩子/pl 数据口/readTarget/writeTarget/parseTarget/navigateTo/arrived/cleanup); Press/Mix/Crank/Power 迁移 (删 readPos×4/navigateTo×4/arrived×4/cleanup×4, Press 122→95 行); RunningBelt readPos 委托 parseTarget
- 设计裁定: 导航语义保持 TLM BehaviorUtils 原样 (不换 PathingApi — 采集域专用, 换则改变行为); Power 历史键名 "pos" 经 targetKey 钩子兼容; Crank 每 tick 现找目标不存坐标 (未用 readTarget)
- 验证: 双编译 + 全量测试通过 (与 C3 同批 354 全绿)
1. grep 全量盘点 navigateTo/arrived/readPos 重复面 (Press/Mix/Crank/RunningBelt/Power/Assembly + BellRing/Jukebox/BlockInteract 等)
2. 新基类 task/pipeline/MoveToBlockStateMachine<E> extends TaskStateMachine<E>: navigateTo (走 PathingApi 保挖穿/垫柱兜底, 防 v79.26.x 回归) / arrived (distanceThreshold() 可覆写钩子, 默认 9.0) / readPos (收口读写; CSV 表示暂不动 — 数据迁移另议)
3. 试点 PressPipeline 迁移 → 双编译+单测 → 其余批量
4. 风险: create 门控代码缺 create 环境无法 gametest 回归 — 单测覆盖转移图 + 冒烟目测

**C3 — 注册面 fail-fast + 缓存声明式清理 (批 3c) ✅ 已完成 (2026-08-13)**
- C3a: TaskRegistryManifest 纯清单 (ALWAYS 8/GATED 8/PASSIVE 7) + register/registerPassive 防重 + 注册完整性 fail-fast (TaskRegistry clinit + PassiveSenseRegistration.init) + TaskRegistryManifestTest 3 用例
- C3b: MaidUnloadRegistry.registerCache 声明式清理 API + MaidAssemblyInventory.CACHE 迁移 (删 0 调用 clearCache); 其余清理为多键+NBT 语义保留显式登记 (实证裁定: 统一键移除 API 只适用纯 map 型缓存)
- 验证: 双编译 + 354 全绿
1. TaskRegistry manifest 测试: 声明式 DEFS 清单抽纯常量 (仿 PacketDef — TaskRegistry clinit 含 ModList.get(), 纯 JVM 必炸 #174, 需先抽清单再断言)
2. MaidUnloadRegistry 加 registerCache(Map) 便捷入口, 12+ 缓存逐个迁移声明式清理
3. 验证: 双编译 + 单测 + 清单测试 (对齐 NetworkPacketManifestTest 范式)

