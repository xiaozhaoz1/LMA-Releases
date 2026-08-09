# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.9.27 (2026-08-08) — v79.26 卡顿修复 + 女仆列表/属性屏大面板重设计 + 3D 预览半身修复

### v79.26.2 Changed (2026-08-08, 实测 "用原版那个3d旋转背景吧, 记得去模糊" + "加载世界要卡很久")

- **女仆列表背景改原版主菜单旋转全景** (用户裁定: "我说的是背景, MC原版自带的那个旋转背景" — 旧仓库 v75.3 女仆选择屏用过 `Screen.renderPanorama`, v79.25 重写独立屏时丢失): `PanoramaRenderer` 双平台类均在 **`net.minecraft.client.renderer`** 包 (本地 decompile 实证, 不在 gui.screens — 此前 WebSearch 误导 import 错包编译失败), 构造收 `CubeMap` (MainMenuScreen 同款); 1.20.1 `render(float, float)` 无参版传 1.0F / 1.21.1 `render(GuiGraphics, int, int, float, float)` 传 width/height (编译实证双平台签名不同); **不调 super.renderBackground** — 1.21 默认含 renderBlurredBackground 背景模糊, 用户明确"去模糊是为了让字不模糊"; 纸感面板 + 深色文字保留浮在全景上
- **★加载世界卡很久根因 (日志实证 19:08:04-19:09:44)**: 进世界后 AnimFileSyncPacket 动画同步 **7 个包逐包 5.5 秒间隔 (共 40 秒)**, 每包渲染线程全量 reload 链: StartupLoader.reload + DynamicAnimationResources.reload + remergeAll (8 次磁盘 IO+解析) + **YsmAnimInjector.injectHaqiIfNeeded (22 模型包 × 8 源文件 × ~3 次读 + Gson 解析 ≈ 528 次磁盘 IO)** + [LMA/Registrar] ISS 未缓存 WARN 每包刷。修复三件套: ① **YsmAnimInjector 源文件指纹快检** (fileName+size+mtime 串, 8 次 lstat 替代 528 次内容读; 源只在 AnimSync 落盘时变 → 指纹不变零 IO 跳过; builtin 未就绪/遍历失败不缓存指纹下次重试 — 竞态兜底) ② **AnimFileSyncPacket 防抖** (handleClient 只落盘, 2 秒无新包 flushPending 统一 reload 一次 — 7 次 5.5 秒卡顿 → 1 次; 由 YsmReloadListener.onClientTick 每 tick 驱动) ③ Registrar ISS 未缓存 WARN 只打一次 (后续 DEBUG 静默)
- 验证: 双节点编译 ✅ / 单测 ✅ / 打包 unzip 验 jar ✅ / 部署双端 (轮换 .bak-0808g)

### v79.26.1 Fixed (2026-08-08, 实测 "主界面的女仆列表怎么是英文的, 改中文")

### 卡顿修复 (日志风暴实证: 1935 行日志中 1664 行是 LMA — 用户"删了 A* 怎么进游戏卡那么多")

- `[LMA/YsmReload]` 1002 行: tick 补全遍历 **123 个 YSM 模型文件 × 8 动画 = 984 次磁盘读取 + JSON/Molang 解析**, 全在 Render thread 同步跑 4.3 秒 — **进世界卡主因**。修复: 源文件解析缓存 (磁盘 IO + 解析 984→8 次, 剩余纯内存 putAnimation 合并) + IdentityHashMap 幂等去重 (同 AnimationFile 实例同源只合并一次; F3+T 重建新实例自动重合并, 内容相等不误跳) + 合并日志降 DEBUG
- `[LMA/Provider]` 419 行: TLM 每帧调 getMagicCastingState/getAnimationBuilder, **每帧 2-3 条 INFO** — 游戏内持续卡顿源。修复: 降 DEBUG (FIRST CALL 保留 INFO)
- `[LMA/YsmInject]` 179 行: 启动一次性逐模型刷屏 → 降 DEBUG

### 女仆列表屏重设计 (用户裁定 "你做的女仆列表不好看, 重新做, 以前那样挺好的为什么要换")

- MaidListScreen 回归 **320×240 大面板** (v79.25.2 的 256×256 太小太挤): 宽列表 170 (行高 22) + 右侧大 3D 预览 (50 缩放) + TLM 棕色面板背景保留
- **行信息双行**: 名称 / `Lv N`(金) + `❤ x/y`(红) + 距离格(灰右对齐) — MaidEntry 扩展 level/health/maxHealth (TLM 等级契约 exp/120 + getHealth/getMaxHealth, 网络包 12/13 序列化同步)
- **3D 预览半身修复 (错题 #137)**: 1.21 的 10 参 renderEntityInInventoryFollowsMouse 是**区域+缩放+yOffset** 语义 (x1,y1,x2,y2 — TLM 自家 67×112 区域, AbstractMaidContainerGui L562), 旧代码传 px+45,py+45 = 45×45 小框 → 下半身被框裁掉只剩半身; 1.20 的 7 参是中心点+缩放 — 照抄参数位置即错 (错题 #136 同族: 双平台方法语义差异)

### 属性屏大方化 (用户裁定 "参数界面也扣扣索索, 大方好看一些重新设计")

- MaidAttributeScreen 面板 **420×300 → 480×360**; 行高 14 → **22**; 组标题 18 → **24** 金色大字 + 装饰短横线; 值右对齐金色 (0x9C6B1F); 双层边框纸感卷面板 + 顶部女仆名金 + 分隔线

### v79.26.1 Fixed (2026-08-08, 实测 "主界面的女仆列表怎么是英文的, 改中文")

- **列表行/预览区硬编码 `Lv N` → lang key** `gui.littlemaidmoreaction.maid_list.level` (zh `等级 %s` / en `Level %s`, translatable 带参数) — v79.26 双行信息新加的行内唯一英文源; 其余屏内 literal 全中文 (grep 全 screen 目录验证)
- **★打包坑: jar 内 lang 陈旧** — neoforge jar 从 `build/classes/java/main` 打包 (copyResourcesToClasses 产物), 但 jar task 不依赖它 → lang 改动后 jar 里 zh_cn.json 停留在旧版 (缺 v79.25 GUI key), 双平台按钮/文本 fallback 显示 key 原文。修复链路: 清 `build/generated` (stonecutter merged) → `--rerun-tasks` 重打包 → **neoforge 必跑 `:neoforge:1.21.1:copyResourcesToClasses --rerun-tasks`** (CLAUDE.md 既有规则) — 之后 jar 内 zh_cn.json 1966B 全 key 齐。**教训: 打包后必须 unzip 验 jar 内资源, 不能只信 BUILD SUCCESSFUL**

## 0.9.26 (2026-08-08) — v79.25.2 女仆列表服务端全维度扫描 + TLM 棕色面板 GUI 统一

### v79.25.2 Changed (2026-08-08, 实测 "女仆列表应该显示自己有的女仆不是搜索周围女仆" + "用 TLM 泥土背景, 左边列表右边 3D 和进入属性按钮")

- **★女仆列表数据源 = 服务端全维度扫描**: 新 `network/MaidListQueryPacket` (C2S, forge ID 12 / neo playToServer) — 服务端 `player.server.getAllLevels()` 全维度 `getAllEntities()` 过滤 `EntityMaid + isAlive + getOwnerUUID().equals(player)` → 按 distSqr 排序; 新 `network/MaidListResponsePacket` (S2C, forge ID 13 / neo playToClient) — `record MaidEntry(uuid, name, dimension, distSqr)` + 客户端静态缓存 tick 轮询; MaidListScreen 删旧 64 格 AABB 附近扫描 (跨维度/远距离女仆全可见)
- **★TLM 棕色主面板背景 (5 屏统一)**: 新 `screen/MaidPanelStyle` — blit TLM `maid_gui_main.png` 256×256 (touhou_little_maid namespace, 1.20/1.21 ResourceLocation 构造条件化) + 深棕渐变衬底 (0xFF4A3424→0xFF20150C); MaidListScreen (256×256 面板居中: 左列表名字+距离格 / 右 3D 预览 / 底部返回+进入属性界面) + MaidAttributeScreen/LMAConfigScreen (按钮排进面板)/TaskTreeScreen/CompatConfigScreen 全屏背景统一
- **进入属性界面按钮**: 仅本地实体存在时可点 (20t 节流 512 格 box 探测 `getEntitiesOfClass` — 远端/跨维度女仆无实体引用属性屏读不了, 面板显示名字+维度+"不在附近"); 点行只选中不直开属性屏 (防误触)
- **错题 #136**: ClientLevel 无无参 `getEntities()` — LmaCommand 的 ServerLevel 用法不能外推到 MaidListScreen 的 mc.level; `drawCenteredString` 6 参 boolean shadow 版双平台均不存在 (统一 5 参)
- 验证: 双节点编译 ✅ / 单测 ✅ / gametest 7/7 ×2 ✅ / 打包部署双端 (旧 jar 轮换 .bak-0808d)

## 0.9.25 (2026-08-08) — v79.25.1 动画骨名误诊更正 (maimeng 统一) + 界面模糊修复

### v79.25.1 Fixed (2026-08-08, 实测 "ISS 注册的 maimeng 还是没播放, haqi 都能正常播放" + "新界面忘记关模糊")

- **★maimeng 播不出真因 (错题 #134)**: v79.20.6 误诊 — 当时认为 TLM 原版模型骨 = 全小写 → 造 maimeng_vanilla/haqi_vanilla (小写骨) + isYsmModel 分流。实际 TLM 官方模型 winefox.json 骨名 **PascalCase** (Root/AllBody/UpBody/Head/LeftArm/LeftForeArm/.../Tail/Tail2-7) — vanilla 小写动画在 winefox 上 **0 骨匹配** → geckolib 播了但模型不动。管线全通 (触发→写侧→网络→Provider seq=46→合并 123 模型文件→无 "Could not load animation") — 唯一断点是骨名。haqi 能播是巧合: haqi.animation.json 用 **AllBody** (TLM 通用根骨, 双模型均匹配)。注: v79.20.6 对小写骨模型 (灵梦/chen 实证) 的修复真实存在 — 但用户主测试 winefox 是 PascalCase → vanilla 分流对用户场景无效
- **修复**: HaqiPipeline 删 isYsmModel 分流 + 删 HAQI_ANIM_VANILLA/HAQI_OWNER_ANIM_VANILLA 常量 → 统一播 maimeng/haqi (PascalCase 骨: YSM 全匹配 / TLM winefox 系部分匹配 — 头/臂/上身/尾动, 腿骨 Leg/LeftLeg2/RightLeg2 不匹配无妨); StartupLoader.ANIM_PRESETS 删 2 条目; 删 2 资源文件; gametest 断言 → HAQI_ANIM/HAQI_OWNER_ANIM
- **界面模糊修复 (实测反馈)**: MaidListScreen/MaidAttributeScreen 缺 renderBackground 覆盖 → 1.21.1 默认含 renderBlurredBackground (1.20.2+ 行为) → 补覆盖: 1.21.1 分支只 fillGradient 不调 super (跳过模糊); 1.20.1 分支 super (原生即渐变无模糊)。LMAConfigScreen/TaskTreeScreen/CompatConfigScreen 已有 v75.2 模式不受影响。教训: stonecutter 块首行不能是裸注释行 (// 会被剥) — 首行必须是代码
- **教训**: ① 骨名验证必须读实际模型 json (winefox.json), 不能从另一个模型的实证外推 (灵梦/chen 小写 → 以为全 TLM 小写); ② "动画播不出"完整链路全通时, 最后嫌疑 = 骨名匹配 — geckolib 静默无警告
- 验证: 双节点编译 ✅ / 单测 ✅ / gametest 7/7 ×2 ✅ / 打包部署双端

## 0.9.24 (2026-08-08) — v79.22 物品注册点就绪 (饰品撤销, 注册基础设施保留)

### v79.22 Added (2026-08-08)

- **★LMA 首个物品注册点**: `init/LmaItems` — 双平台条件化 DeferredRegister\<Item\> (forge ForgeRegistries.ITEMS / neoforge BuiltInRegistries.ITEM, 逐字镜像 LmaBlocks 结构); 挂载 `LmaRegistrar.registerItems` → forge LittleMaidMoreAction / neoforge LmaNeoForgeEntry 构造器; **当前零物品** — 注册点就绪, 后续物品在此注册 (javadoc 含双平台注册示例)
- **饰品撤销 (裁定)**: 曾注册 3 饰品 (task_progress/rule_trigger/wireless_io_boost + MaidBaubleApi 门面 + 3 行为 + bindMaidBauble + addMaidTips 提示 + 测试/资源/lang) — "我还没想要加饰品, 现在是先把注册写完方便以后注册" → 全部删除, 只留注册点
- **TLM 桥接盘点** (ILittleMaid 22 方法): 已接 8 + addMaidTips (客户端物品提示, 有物品后接) + 3 预留钩子 (addMaidBackpack/addChestType/addMaidMeal — 需 LMA 自有类型, 无则硬接 = 死代码); 13 不接 (无 LMA 语义 + 1 deprecated)。盘点入 ARCHITECTURE.md §11.10
- 验证: 双节点编译 ✅

### v79.22.1 Fixed (2026-08-08) — ASCEND 依托误放 (草丛搭阶梯)

- **现象**: 女仆搭路时总是往视角前一格搭块 — 放置位置全貌: 桥 = 视角前一格下面 (要去的方向, y=F-1) / 柱 = 跳起的脚下 / ASCEND 依托 = 视角前一格 (y=F, 仅落点不可站时)
- **根因**: PathExecutor ASCEND 放置条件 `canBeReplaced(stand)` — 草丛/花/雪可替换但可站 (下方草方块实心) → 爬台阶交界处有草丛 → 误判"缺依托" → 每格垫一块 = 搭阶梯
- **修复**: 条件 → `!MovementHelper.canWalkOn(...)` (baritone MovementAscend L176-177 同款: 落点脚下不可站才放), 内层保留 canBeReplaced + 放置门; 规划端无需改
- 验证: 双节点编译 ✅ / 单测 34 类 210 全绿 ✅ / 错题 #128 (可替换 ≠ 缺依托)

## 0.9.23 (2026-08-07) — v79.21 任务气泡完整 API 提取 (chatbubble)

### v79.21 Added (2026-08-07, 把任务进度/完成/规则触发气泡提取为完整 API)

- **MaidChatBubbleApi 门面** (chatbubble/ 包, ★裁定不加自定义类型 — 只用 TLM 内置 TextChatBubbleData/ProgressChatBubbleData): showInfo (8s 无节流) / showComplete (§a✔ 无节流) / showFail (§c✘ **600t** 节流 — 沿用 v67.3 语义) / showTrigger (§e⚠ **100t** 节流 — 防信号刷屏) / showProgress (**替换式** — WeakHashMap 每女仆跟踪最新进度气泡, 先 remove 再 add, 防 TLM 5 气泡上限堆积); 服务端直调 ChatBubbleManager, SynchedEntityData 自动同步; § 码颜色 (TLM 无绿色纹理, 裁定)
- **adapter 语义门面**: LmaTaskProgressDisplay 重写为委托 API — friendlyName (15 任务友好中文名) + stateName (18 FSM 状态中文) + showTaskStart/showStep/showComplete(带 count/max)/showFail/showNoContent
- **全量迁移 10 处散点** (裁定全量提取): TaskDispatcher (validate 失败/优先级冲突/超时 → showFail; submit → 开始气泡; complete → 完成气泡 — clearAll **前**读 FLOW_COUNTER/FLOW_MAX_COUNT) + LmaFlowCoordinationBehavior FAILED + MonsterLogPipeline (⚠/✔) + AiControlPipeline 缺前置 + BlockInteractService 绑块丢失 + JukeboxExecute 正在播放 + ChainHarvestExecute 进度 (删 BUBBLE_ID map, 保留 BUBBLE_TICK 节流) + WorldOutput.sendBubble×2 委托
- **步骤气泡新接线**: TaskStateMachine.tick() 首 tick + 每次合法转换 → showStep (替换式; 引擎直接调用, 独立于 onEnter 钩子 — ArmTransferPipeline 覆写 onEnter 不受影响)
- **错题 #126**: 超时气泡缺节流 (同类 fail 刷屏 bug 连带修 — 超时无节流会 30 秒内重复刷屏; 归入 showFail 600t 节流)
- 验证: 双节点编译 / 单测 34 类 210 (新增 MaidChatBubbleApiTest 5 + LmaTaskProgressDisplayTest 6) 全绿

## 0.9.22 (2026-08-07) — v79.20 哈气"对主人"变体 + 表情气泡通用 API

### v79.20 Added (2026-08-07, 女仆对主人哈气 + 表情包 API)

- **对主人哈气变体 (★核心)**: HaqiTrigger 触发节流**对女仆优先** — 先查 2 格内女仆掷概率, 未触发再查 2 格内主人 (getOwner() 在线 ServerPlayer) 独立掷概率; 向后兼容 target_type 读空默认 maid (旧存档无键按对女仆处理)
- **6 项 _to_owner 配置**: enabled_to_owner (默认 **false** 独立二级开关, 总开关 HAQI_ENABLED 控整个管道) / chance_to_owner (0.1) / duration_ticks_to_owner (60) / volume_to_owner (1.0) / hit_chance_to_owner (0.3) / hit_damage_to_owner (1.0); GUI 哈气分类 +6 中文条目
- **表情气泡通用 API (★两次裁定非 haqi 专用)**: `chatbubble/` 包 — HaqiEmojiType (MAID: emoji_10/05/09, OWNER: emoji_01/02/20-24x24, randomPick 纯逻辑随机) + HaqiEmojiBubbleData (implements IChatBubbleData, ID littlemaidmoreaction:haqi_emoji, existTick 15s) + HaqiEmojiChatBubbleRenderer (@OnlyIn CLIENT, 按文件名精确过滤资源, 缺资源回退 emoji_0.png) + HaqiEmojiApi 服务端门面 (getChatBubbleManager().addChatBubble)
- **对主人声音**: 固定 littlemaid_peco 包 11 idle 子集随机 (idle2/23/32/40/53/61/64/66/67/77/78) — HaqiOwnerVoicePacket 双形态网络包 (sendToTracking) + 客户端 PecoHaqiSoundPlayer (仿 MaidSoundInstance) + PecoHaqiSubsetLoader (Files.walk tlm_custom_pack + OggReader 读盘; zip 兜底全随机 + 告警); 1.21 ICustomSoundBuffer 免费接入, 1.20 自挂 PlaySoundSourceEvent
- **挥击主人**: 掉血 (CombatOutput.mobAttack) 主人不反击; TLM canAttack 硬排除玩家仅影响 AI 目标选取层, 不影响直接 hurt (错题 #118)
- **gametest 7/7 双节点**: 新 lmaHaqiHitOwner (自构造注册 ServerPlayer + spawnInvulnerableTime 反射清零)
- 验证: 双节点编译 / 单测 186 / gametest 7/7 ×2

### v79.20.3 Changed (2026-08-07, 裁定: "A*不适合女仆留着干嘛" → "删 A* 但加 BFS 替代")

- **A* 裁撤, 网格 BFS 替代 (★)**: 删 `AStarPathFinder` (启发搜索矿洞不适配女仆) → 新 `BfsPathFinder` — FIFO 无启发无代价, 移动集裁剪 (traverse 走/挖/桥 + ascend 挖头/ASCEND-place + descend 下 1-2 格; 跑酷删 — 全局禁用, 搭柱删 — 被 ASCEND-place 覆盖); 保留 bound 剪枝 + 链式放置支撑。链路 (v79.20.3b 统一入口, 裁定 "都加"): PathingApi.findPath 内部分支 — 激进 (EXPLORER/allowMine) → 直线优先 → null → BFS; 其他 (SAFE/BRIDGE) → 直接 BFS; TLM 模式 → TLM 原生导航 (Mode.TLM 分支不动)。PathExecutor.plan/precomputeNext/hasPath 预检全走统一入口, findPathSafe/findPathExplorer 委托 findPath
- **MAX_NODES 15k→12k ("16格就多少节点")**: 有 bound 目标 AABB 25×25×17 = 10,625 格硬上界, 15k 永不触发; 12k = 上界 + 10% 缓冲, 仅 near(16)/anyOf 无 bound 时防爆
- **测试**: BfsTest 16 (删 4 跑酷/搭柱) + LinePathPlannerTest 17 — 单测 199 / gametest 7/7 ×2 全绿

### v79.20.6 Fixed (2026-08-07, "怎么有的女仆做 maimeng 有的一会儿 haqi 一会直接没动作了" + "TLM 原版狐狸" + "你忘记加原版动作了你个猪")

- **TLM 原版模型哈气/卖萌无动作 (★, 错题 #125)**: maimeng/haqi 动画骨 = PascalCase **YSM 女仆骨** (Head/LeftArm/AllBody/UpBody/Tail_1-4), TLM 原版模型 (灵梦/chen 实证) 骨 = **小写** (head/body/armLeft/armRight/legLeft/legRight/tail) — 非 YSM 女仆走 geckolib ISS 通道: 动画定义有 + Provider 请求播放, 但**骨名全不匹配 → 播了但模型不动 → 静默"没动作"** (YSM 模型骨匹配正常 — 与"有的有动作有的没有"吻合)
- **修复**: 新增 TLM 骨版动画 `maimeng_vanilla.animation.json` (点头卖萌 0.5s) + `haqi_vanilla.animation.json` (弯腰哈气 1.67s) — 小写骨 + **纯数组 keyframe** (geckolib3 兼容; post+catmullrom 是 YSM 扩展格式); HaqiPipeline.enterLook `isYsmModel` 分流: YSM → maimeng/haqi (逐行不动) / TLM → maimeng_vanilla/haqi_vanilla; StartupLoader.ANIM_PRESETS +2 (自动复制 + ISS 注册 + AnimFileSyncPacket 全量同步); gametest 断言同步 (测试女仆非 YSM → vanilla 名, 错题 #79 教训)
- **测试**: 编译双端 ✅ / 单测 199 ✅ / gametest 7/7 ×2 ✅ / 部署双端 + 上传 LMA-Releases main 分支

## 0.9.21 (2026-08-07) — v79.19p 工具判断 API (泥土→铲子, 矿→镐, 树→斧)


### v79.19q Fixed (2026-08-07, 实测: "铲子直接被替换没了, 只剩稿子了")

- **换工具丢旧工具 (★根因)**: `maid.setItemInHand(新工具)` 直接替换主手槽位 → **旧工具不自动放回背包 → 永久丢失**; 铲↔镐来回换时每轮丢一个 (实测铲子消失)
- **修: `swapTool` (三处调用点统一)**: 新工具从背包槽位 `extractItem(slot, 1, false)` 提取 (保证写回, 记忆 #76 同款) → 旧工具 `insertItem` 放回该空槽 → 再 `setItemInHand`。TLM 无换工具专用 API (已查源码 EntityMaid/ItemsUtil) — 走 IItemHandler 交换
- 验证: 双节点编译 / 单测 0 failed / gametest 6/6 ×2 / jar 0.9.10 (v79.19q) 已部署双节点

## 0.9.18 (2026-08-07) — v79.19m 挖矿寻路 1 秒刷新 + 16 格内矿必可达 (跳过集 TTL)

### v79.19p Added (2026-08-07, "加工具判断api, 让女仆挖泥土会换铲子, 挖矿换稿子")

- **工具判断 API (★核心)**: `ToolJudge.ToolType` 枚举 (PICKAXE/AXE/SHOVEL/NONE) + `suitableToolType(BlockState)` — 按 MC 标准 `MINEABLE_WITH_*` tag 映射方块 → 合适工具类型 (泥土/沙/沙砾→铲, 矿/石→镐, 原木→斧, 无 tag→NONE 任意可挖); `isSuitableTool` / `matchesToolType` 组合判定
- **ToolStateReader 加 `isShovel`**: 与 isPickaxe/isAxe 同模式 (instanceof ShovelItem)
- **目标驱动换工具 (★需求)**: `ChainHarvestExecute.ensureToolFor` — 找到目标后按**方块**合适类型从全背包选 tier 最高可用工具, 不再按模式 (ORE→镐) 硬编码: 挖泥土换铲子、挖矿换镐、砍树换斧; 开脉前/垂直挖穿挖脚下/寻路前全接入
- **扫描谓词与手工具解耦 (★关键)**: `scanTool` = 模式默认钻石镐/斧 — 手拿铲 (挖完泥土) 也能扫到矿, 手拿镐也能扫到泥土 (原谓词用当前手工具 canHarvest → 拿错工具时目标全被过滤, 永不换工具的死锁); 等级不够的矿由 tryStartVein 真实工具 canHarvest + 跳过集兜底
- **头部 ORE 加铲豁免**: 手拿好铲不换镐 (防每轮换镐↔铲抖动); 跳过集按真实手工具分组 (与扫描工具解耦)
- 验证: 双节点编译 (BlockTags.MINEABLE_WITH_* 字段名编译验证) / 单测 0 failed / gametest 6/6 ×2 / jar 0.9.10 已部署双节点

## 0.9.18 (2026-08-07) — v79.19m 挖矿寻路 1 秒刷新 + 16 格内矿必可达 (跳过集 TTL)

### v79.19m Fixed (2026-08-07, 实测: "明明周围16格内有矿物但就是不走过去挖" + "挖矿寻路要每秒刷新" + "确保女仆知道周围16格矿物能去挖")

- **扫描间隔 60t → 20t (★要求)**: `CHAIN_SCAN_INTERVAL` 默认 3 秒 → 1 秒 — 无目标扫描每秒刷新, 有矿不再干等 3 秒
- **跳过集 TTL 过期重试 (★核心)**: FAILED 目标原**永久**进跳过集 (仅换工具 tier 才清) — 失败一次永不挖; 日志实证 10:58:14 寻路失败 {-7,-52,-29} 后该矿永久排除。新 `SKIP_TTL=600` (30 秒) + `SKIP_AT` (pos→gameTime) — 每次 `findNearestValid` 先过期清理, 路径可能因挖脉/搭路变化, 30 秒后自动重试
- **采集寻路默认 safe → explorer**: safe 纯走绕绕不过山/墙 → FAILED → 跳过集; explorer 挖+搭+桥全开 — 16 格内矿必可达 ("确保女仆知道周围16格矿物能去挖")
- **FAILED 日志加 failReason**: PathExecutor 看门狗超时原因 (原只有目标坐标, 无原因)
- 验证: 双节点编译 / 181 测试 0 failed / gametest 6/6 ×2 / jar 0.9.7 已部署双节点

## 0.9.20 (2026-08-07) — v79.19o 发呆三重根因修复 + 16 格=半径 16 格 + 卡边缘居中伺服

### v79.19o Fixed (2026-08-07, "寻路还是在原地发呆" + "我说的周围16格是半径16格" + "明明头上三个就是矿" + "女仆卡在边缘的情况有考虑吗")

- **MINE 步看门狗 100t → 400t (★发呆主因)**: 渐进破坏 (mineStep 每 tick getDestroyProgress 累积) 挖 1 块石头 ~150t (7.5 秒) > 原 STALL_LIMIT 100t (5 秒) → **每次挖矿必看门狗超时 FAILED** → 跳过集 → 发呆 ("对着地上发呆")。新按动作区分: MINE 步 400t (20 秒, 覆盖最慢石镐+余量), 其他步 100t
- **idleScan 到达判定恢复 3 格球 (★"头上三个就是矿")**: v79.19n 同步 1 格邻域改过头 — 头顶 3 格矿 distSqr 9 ≤ 9 在破块门内可直接挖, 原 1 格邻域排除 → 去寻路 → 绕不过 → FAILED → 跳过集 → 发呆。**职责分层**: 寻路目标 oneAway (走动必到旁 1 格/头顶, 卡极限格问题已解决) + 到达判定 3 格球 (原地能挖直接开脉) — 不冲突
- **16 格 = 半径 16 格 (chunk 换算修正)**: `SenseApi.findNearestBlock` 原 `radius/16+1` → 16 格 → 2 chunk = 32 格半径 (超预期 1 倍, 扫描更慢); 新 `Math.max(1, (radius+15)/16)` ceil — 16 格 = 1 chunk = 半径 16 格
- **WALK 卡边缘居中伺服 (baritone centerPlayer 同款)**: 到达判定满足 (邻格) 但实体偏离 dest 中心 > 0.3 格 (站格边缘) → `setDeltaMovement` 伺服收敛中心再推进 (速度 `min(0.2, d*0.5)` 近减速) — 原停在格边 → 下一步从边缘出发 → 漂移累积 → "女仆卡在边缘"
- **跳过集 TTL 30 秒 → 1 秒 (★裁定)**: "把跳过集删了,或者 1s 就刷新跳过集"。`SKIP_TTL` 600 → 20t — 与 CHAIN_SCAN_INTERVAL 同步 → 每次扫描都是全新候选 (等效 1 秒重试, 保留同轮防重复: FAILED 目标不挡队首, 单次扫描选下一个可挖目标)
- **垂直挖穿 (★实测: "女仆对着地下3格的矿看了半天, 我把上面的方块挖了才下去挖")**: 目标在脚下 ≤3 格但整格 distSqr > 9 (水平偏移+深度组合) → 到达判定/破块门 (都 distSqr≤9) 不过 → 原走寻路, TLM 导航到不了地下 → 站着看 (日志实证: 12:23:54-12:24:40 每 1-3 秒 "start→navigating" 循环, 无 FAILED 无动作; 挖开方块 → 12:24:40 直接开脉)。修: idleScan 到达判定后加**垂直挖穿分支** — 目标在脚下 ≤3 格 → 每 tick 挖脚下石头逐层下 (destroyBlock, 液体/基岩 canDestroyBlock 不过 → 回退寻路; destroyBlock 失败 → 回退寻路不死循环), 到矿层自然进 3 格球 → tryStartVein
- **MINE 看门狗 20 秒 → 12 秒 (裁定)**: `MINE_STALL_LIMIT` 400 → 240t — 挖 1 块石头 ~7.5 秒, 12 秒仍有余量
- 验证: 双节点编译 / 186 测试 0 failed / gametest 6/6 ×2 / jar 0.9.9 已部署双节点

## 0.9.19 (2026-08-07) — v79.19n 寻路到达 1 格邻域 + 头顶净空挖掘 (不再卡极限格)

### v79.19n Fixed (2026-08-07, "矿物在三格外但寻路觉得在三个内" + "寻路加特殊模式挖头顶方块" + "头顶一格挡住跳不起来")

- **寻路目标 reach (3 格球) → oneAway (1 格邻域 3x3x3, ★核心)**: 原 A* 停在 (3,1,0) 等斜/极限格 (3D ≤ 3 但整格 distSqr > 9) → 破块门挖不到 → "矿物在三格外但寻路觉得在三个内" 死锁。新到达 = 旁 1 格 (含对角) / 正上方 (站目标头上挖脚下) / 正下方 (目标在头顶 → 挖头顶) — 3D 距离 ≤ √3 ≈ 1.73 < 破块门 3 格, **到达必可挖, 永不卡极限**
- **idleScan 到达判定同步 1 格邻域**: 与寻路目标同一语义 (原 3 格球不一致 → 寻路 ARRIVED 但判定不挖) — 到达后立即 tryStartVein, 流程不停: 挖完 → 重扫 → 下个矿
- **ASCEND 头顶净空挖掘**: 跳 1 格台阶头最高 ≈ feet.y+3 需 2 格净空 — 实测 "头顶一格挡住跳不起来" → 跳前每 tick 无状态检查 f.above() + f.above().above(), 实心可挖先挖 (渐进跨 tick) — 挖空自动跳; A* 侧已有同款代价 (tryAscend src y+1 / dest y+2), 执行端补齐
- 新增 NavGoalTest 5 用例 (oneAway 旁/对角/头脚下/极限格不算/目标格 sacred)
- 验证: 双节点编译 / 186 测试 0 failed / gametest 6/6 ×2 / jar 0.9.8 已部署双节点

## 0.9.17 (2026-08-07) — v79.19l ASCEND 跳跃拉扯 (位置伺服朝 dest)

### v79.19l Fixed (2026-08-07, 实测: "女仆跳了以后没有移动, 就在原地一直跳")

- **ASCEND 跳跃拉扯 (★核心)**: 女仆跳起时水平速度 = 0 (导航目标 = 脚下 approach 格, dist=0 → 无水平移动输入; baritone 跳跃中持续移动输入, LMA 无) → 垂直跳 → 落回原地 → 反复跳。修: **空中每 tick 水平位置伺服朝 dest 中心** (`setDeltaMovement` 方向 = dest 中心 - 当前位置, 速度 `min(0.25, dist*0.4)` 近减速) — 收敛到 dest 投影内 0.2 格 → 落回站上 dest。跳门加 `onGround` (空中 jump 无效, 原反复无效 jump)
- 验证: 双节点编译 / 181 测试 0 failed / gametest 6/6 (forge) / jar 0.9.6 已部署双节点

## 0.9.16 (2026-08-07) — v79.19k 垫方块以 maid_useful_task 为准 + 完成判定 onGround + 寻路 4 档重定义

### v79.19k Fixed (2026-08-07, 实测: "走不上一格方块/原地跳" + "视角前面一格的下面一格放方块卡住" + 点名"你认真读了女仆实用任务的垫方块没有,现在的垫方块是错的" + 全局寻路 4 档)

- **完成判定加 onGround (★核心, maid_useful_task BlockUpPlaceBehavior canStillUse 同款)**: placeStep 已实心 `return foot.equals(dest)` + ASCEND `yield f.equals(dest)` — 女仆跳跃中 feet.y 跨整界 → blockPosition.y = dest.y (空中) 即误判完成 → 空中推进 → 下一步乱序 → 落回原层反复跳 = "放完块还在原地跳跳不上去"。新 `foot.equals(dest) && maid.onGround()` — 必须落地才算完成 (1 格台阶峰值 1.25 > 1.0, 落回台阶站上, 不误伤)
- **柱放置门: 浮点 → 层级 (maid_useful_task 双条件 `canBeReplaced(below) && canBeReplaced(pos)` 同款)**: 原浮点门 `y > dest.y+0.1` 需跳高 ≥1.1 格, 女仆跳高不足/衰减 → 永不过 → 原地跳死; 新身体格 (blockPosition) 可替换 = 已升 1 层才放, 只需跳高 ≥1.0
- **柱放置格: 静态 aux → 动态 blockPosition.below() (maid_useful_task 同款)**: 原放规划静态 aux, 女仆漂移/推挤 → 放错位 → "视角前面一格的下面一格放方块卡住"; 新放动态脚下格, 跟手垫到位
- **寻路模式 4 档重定义 (指令)**: 1.TLM原版 (新 `tlm` — 不走 PathExecutor, TLM 原生 Brain 导航直达, ChainHarvestExecute.moveTo 分支) / 2.新寻路 safe (A* 纯走绕) / 3.搭方块搭桥 (搭柱+桥不挖) / 4.激进破坏 (挖+搭+桥全开); PARKOUR 删除, 旧 "parkour" 配置 → SAFE 兜底 (兼容)
- 验证: 双节点编译 / 181 测试 0 failed (新增 PathingModesTest 3 用例) / gametest 6/6 ×2 / jar 0.9.6 已部署双节点

## 0.9.15 (2026-08-07) — v79.19j 放置基准修正 + 原地跳根因修复 + 全局跑酷禁用

### v79.19j Fixed (2026-08-07, 实测: "在视角下一格放方块卡住" + "放完一直跳跳不上去" + "全局设置的跑酷没改")

- **柱判定容忍 Y (★核心, baritone MovementPillar 放置门对照)**: placeStep 原 `aux.equals(foot)` 严格 `BlockPos.equals`(含 Y) — 女仆跳跃中 `blockPosition().y = 脚格+1` → aux≠foot → 落入 else 分支 (桥/一般, **直接 tryPlace 无放置门**) → 刚离地 (y≈0.1) 就放 → 身体与块重叠 → 原版水平推挤 → 块没垫到脚下, 每 tick 再跳再放 → "放完还一直跳跳不上去"。新纯函数 `isPillarPlacement` (水平同格 + aux.y ≤ foot.y) → 跳跃中仍柱分支 → 放置门 `position().y > dest.y+0.1` 生效 (baritone 无条件全局, 不因 feet block 上移丢失)
- **wrong-Y 恢复加深度限制**: 原 `foot.y < dest.y && onGround → jump` 无深度限制 → 掉 2 格深坑跳 1.25 格跳不上 → 原地跳到看门狗 FAILED; 新仅 `foot.y == dest.y-1` 跳回 (1 格浅坑), 深坑不跳 → 导航走近 / 看门狗 FAILED (baritone MovementAscend L161: `feet.y < src.y → UNREACHABLE` 语义)
- **全局跑酷禁用 (否决 v79.19d 补全)**: PathingModes PARKOUR/BRIDGE 的 `allowParkour=true` 残留 → 全模式 false (PARKOUR 降级 = SAFE, 枚举保留兼容既有配置); ActiveTaskConfig 注释同步
- 验证: 双节点编译 / 178 测试 0 failed (新增 isPillarPlacement 两用例) / gametest 6/6 ×2 / jar 0.9.6 已部署双节点

## 0.9.14 (2026-08-07) — v79.19i 寻路 3 格挖矿 + ASCEND 台阶卡死修复

### v79.19i Fixed (2026-08-07, 实测: "寻路时不挖周围矿物" + "走不上一格方块, 抽风站原地不动")

- **NavGoal.reach (baritone GoalThreeBlocks 同款)**: 3D `distSqr ≤ 9` 即达 (女仆攻击距离 3 格), 目标格 sacred 不可挖 — 纯 JVM, 复用 `VanillaConstants.ARRIVE_DIST_SQR` (唯一权威 reach 常量)
- **寻路目标 interact → reach** (ChainHarvestExecute.moveTo): 原 1 格相邻寻路 vs 3 格挖矿能力脱节 → 3 格内即 ARRIVED, 与破块门一致
- **idleScan 到达判定 2 格 → 3 格**: `distSqr <= 4.0` → `<= ARRIVE_DIST_SQR` (与破块门一致)
- **nearPass 3 格球近扫** (核心需求: "即便寻路没到指定地点依旧要搜寻周围 3 格矿物"): idleScan 节流检查前手写 3 格球扫描 (x/z/y ∈ [-3,3], 3D distSqr ≤ 9 ≈ 113 格), 独立 5tick 轻节流 (不卡 CHAIN_SCAN_INTERVAL), 快过滤 (skip/matches/allowed/canHarvest) → tryStartVein — **寻路途中经过的矿直接开脉挖**。SenseApi.findNearestBlock 的 radius 是 chunk 半径语义 (radius/16+1, 最小 16 格) 不可复用
- **ASCEND 不再导航不可达格** (PathExecutor): 原导航 dest (台阶顶格 = 方块内不可达) → TLM 导航停 2 格外 → 跳门 `|dx|≤1.2` 永不过 → 看门狗 FAILED 原地抽风; 改导航 `approachCell` (v79.19f 柱分支同款 0.5f,0) → 女仆稳在 src 中心 dx≈1.0 过跳门 → jump 上台阶
- **moveTo FAILED 加日志**: `"[ChainHarvest] 女仆 {} 寻路失败 目标 {} 跳过"` — 原静默无诊断 (卡死只能靠日志猜)
- 验证: 双节点编译 / 176 测试 0 failed (新增 reach 三用例) / gametest 6/6 ×2 / 待游戏内实测 (寻路途中挖 3 格矿; 1 格台阶正常走上)

## 0.9.13 (2026-08-07) — v79.19h PLACE/ASCEND 执行对齐 baritone Movement 语义

### v79.19h Fixed (2026-08-07, 实测: 搭桥时"在面前放挡住自己的方块" + "站原地不动")

- **placeStep 桥分支重写 (baritone MovementTraverse 语义)**: 原每 tick 导航 dest 同时放置 → 块未落先走进沟 → 掉坑; 现在 **aux 未实心时只放置不导航 dest** (keepAlive 保留), 块确认实心后才导航 dest — **先放置后跨越**
- **wrong-Y 恢复** (baritone Traverse L250-257): 两分支加 `foot.y < dest.y && onGround → jump` — 掉坑后自动跳出, 不再等看门狗 100t 静默 FAILED
- **放置门回退目标 = approach 格非 dest** (错题 #93): 新纯函数 `approachCell(dest, foot)` = dest 向女仆方向的水平符号位移 + foot.y (规划节点安全可站格); 门未过 → 导航 approach 走回节点 — 原导航 dest (跨沟不可达) → 原地徘徊
- **ASCEND 加放置子阶段** (baritone MovementAscend L176-191): `dest.below()` 缺依托且可替换 → 3x3 门内 → `tryPlace(dest.below())` 从边缘垫块 (不导航 dest); 依托确认后走现有跳门。无状态 — 块出现后世界状态自动跳过放置 (不违 #77)
- **tryAscend 落点缺依托加放置分支** (A* 层): `allowBridge && dest.below() 可替换 && isAscendPlaceable (支撑面向下/4 侧, 排除 src 列)` → 发 ASCEND 代价 `JUMP_ONE + costOfPlacingAt` — 与挖分支并存, A* 按代价选 (baritone 同)
- **isBridgeable 补 aux 检查 (错题 #94, 测试暴露)**: `dest.below()` 必须可替换且非 liquid/hazard — 修复在 lava 上搭桥 (`[PLACE(3,2,1,aux 3,1,1), PLACE(4,2,1,aux 4,1,1)]` 实测路径)
- **tryPillar body 格放宽 (错题 #95, 测试暴露)**: 柱链 body 格 (y+1) 允许可挖掘 (与 v79.19f head y+2 语义同) — 修复链中第二根柱在头部 STONE 处断裂 (path=null)
- 验证: 双节点编译 / 173 测试 0 failed (`--rerun-tasks`) / gametest 6/6 ×2 / 待游戏内实测 (跨沟: 先放块后过沟不掉坑, 块不挡脸)

## 0.9.12 (2026-08-07) — v79.19g 放置距离门 + 柱位移到中间 (澄清)

### v79.19g Fixed (2026-08-07, "女仆只能在脚下周围那九格放方块" + "搭柱是跳起来搭")

- **放置距离门** (核心需求): placeStep 每 tick 查 aux 距女仆脚格 — 水平曼哈顿 >1 或 Y 差 >1 → 先走近再放 (导航 dest 0.5f,0)。方块只能放女仆脚下 3x3 (九格) 内, 贴身搭不隔空远放。撤销误解读的 A* "连续桥 ≤9 限制" (高架柱路径可绕过, A* 层限桥无效; 澄清 = 每次放置的位置门, 非桥长)
- **柱位移到中间** (maid_useful_task Schedule→UP 严格对齐): 柱分支 boundingBox 不在柱格 1x1 → 导航 aux (柱脚格可站, 0.5f,0) 走到格中心 → 对齐后清零水平速度 (`setDeltaMovement(0, mv.y, 0)`, alignOrTryMove 同款) → 头顶实心先挖 → onGround 跳 / 滞空放。原拉拽 0.02 太慢 (实测不走中间)
- **看门狗 FAILED 不再气泡** ("看门狗提示不要气泡了"): PathExecutor sweep 删气泡块 + BUBBLE_COOLDOWN/LAST_BUBBLE/WeakHashMap import; ChainHarvestExecute "无法到达目标" 气泡删 (保留 addSkip + 立即重扫)
- **挖矿到达 2 格** ("周围两格没有矿物就搜索其他矿物"): ChainHarvestExecute idleScan `distSqr <= 1.0` → `<= 4.0` (女仆攻击距离 3 格, 2 格内直接开脉)
- 验证: 双节点编译 + 全测试 / jar 复制 1.21.1 mods / 待游戏内实测

## 0.9.11 (2026-08-07) — v79.19f 柱对齐物理拉拽 + 头顶实心先挖 (maid_useful_task 参照)

### v79.19f Fixed (2026-08-07, 实测: 女仆卡一格方块不走过去 + 头上有方块顶头不挖)

- **placeStep 柱对齐门** (maid_useful_task `BlockUpPlaceBehavior.alignOrTryMove` 移植): 女仆 boundingBox 不在柱格 1x1 内 → `setDeltaMovement` 水平拉拽 (0.02 力度, 保留 y 速度) — 跳放**不靠导航** (TLM 导航遇挡路方块不走 → 女仆停 2 格外 "卡一格方块不走过去", 实测); 柱阶段不再导航 dest (dest=柱顶在方块内, 导航目标不可达 → 女仆徘徊)
- **头顶实心 → 先挖净空再搭** (maid_useful_task: 搭柱中头顶挡路 → 放弃 UP 转 DESTROY 挖掉再搭): `tryPillar` 头顶 y+2 实心**可挖时不拦截** (原直接 return → 女仆顶头不挖干站着; 静态 A* 无法模拟"挖→世界变→搭"时序, 原地 mine 步 dead-end 实测); 执行端 `placeStep` 柱分支每 tick 查 `foot.above(2)` 实心 → 复用 MINE 渐进破坏挖掉 → 挖完推进 → 下一柱步正常搭
- **placeStep 速度/距离修正**: `1.0F, 2` → `0.5F, 0` (漏改 — 原跑 + 2 格外即算到达, 女仆停 2 格外; maid_useful_task 全用 0.5f,0 精确走到格中心)
- 验证: 双节点编译 + 全测试 (新 AStarTest 头顶实心岩浆墙用例) / jar 复制 1.21.1 mods / 待游戏内实测 (低天花板下搭柱: 应自动挖头顶再搭)

## 0.9.10 (2026-08-07) — v79.19e 台阶显式跳 + 实心前挖穿 (baritone MovementAscend/MineProcess 参照)

### v79.19e Fixed (2026-08-07, 实测: 女仆被面前 1 格方块挡住, 能走上或挖掉却不做)

- **ascend 被压成 walk 纯导航 → 女仆 AI 不自动上 1 格台阶 → 被面前 1 格方块挡住** (实测 "会被自己前面一格的方块挡住, 但明明可以直接走上那个方块")。参照 Baritone `MovementAscend` (跳门 L205-226: 水平距 dest ≤1.2 + 侧向 ≤0.2 + 横速 ≤0.1 → JUMP; SUCCESS = feet==dest): 新增 **`PathStep.StepAction.ASCEND`** 独立动作 + `AStarPathFinder.tryAscend` 改发 ascend (原 walk) — 执行端 `PathExecutor` 新 `case ASCEND`: 导航 + 跳门 (`f.y < dest.y && |dx|≤1.2 && |dz|≤1.2 && lateral<0.5` → `jump`), 完成 = 脚格 == dest
- **tryTraverse 实心方块前加 mine 分支** (baritone 同: 实心前 ascend 走上 或 mine 挖穿 二选一, A* 按代价选): `avoidWalkingInto || !canWalkThrough` 时, `allowMine && isMineable && !isSacred` → `PathStep.mine(dest, dest)` (挖掉走过去)
- **WALK 到达判定收严** (v79.19e): 原 `distSqr ≤ 4` (2 格含 Y) → 提前"到达" → 提前推进 → 目标外卡住 (实测 "卡在矿物 3 格外既不挖也不走"); 改 = **同脚层 && 水平曼哈顿 ≤ 1** (Baritone feet==dest 精确语义容差版); `SWITCH_TOLERANCE_SQR` 16→4 (4 格→2 格)
- **ChainHarvestExecute 到达判定 3 格 → 1 格** (6 邻): 矿物旁 1 格才开挖; `moveTo` FAILED (不可达) → **立即 idleScan 重扫** (原等 3 秒扫描间隔 — "周围 1 格没有矿物就去挖别处的矿直接开始新寻路"; baritone MineProcess 不可达黑名单 + 重扫语义) + 跳过格记录 + "无法到达目标" 气泡
- **速度 0.7f → 0.5f** (实测 0.7f 仍偏快; 1.0F = 跑, 0.7F = 归家行走, 0.5F = 更慢速; 3 处 `setWalkAndLookTargetMemories` WALK/ASCEND)
- **pillar 放置门** (v79.19e 补, 原 v79.19b 无状态版缺): `aux == 脚格` 时滞空即放 → 刚离地 `y < dest.y` → 身体与块重叠 → 原版水平推挤 → 站不上块 → 完成判定永不过 (实测 "先往脚下前一格放, 然后上面放一格就卡住"); 加 Baritone `MovementPillar` 放置门: `position().y > dest.y + 0.1` 才放 — 全滞空窗口, 无窄窗错过
- 验证: 双节点编译 + 全测试 (canSwitchSegments 断言同步 4.0) / jar 复制 1.21.1 mods / 待游戏内实测 (1 格台阶跳上 / 实心方块挖穿 / 矿物 1 格到达)

## 0.9.9 (2026-08-06) — v79.19c PLACE 完成判定修正是走上 + 纯块放置 (状态机否决后两轮重写)

### v79.19c Fixed (2026-08-06, 实测: 面前连搭 2-3 格上不去)

- **v79.19b 完成判定只查 aux 实心 → 桥步放完即完成, 漏"走上 dest"阶段**: 女仆原地连放 2-3 格垫块, 自己没站上去 (实测 "一直在自己面前搭方块...上不去")。对照 Baritone/Numen MovementTraverse: 放置格 = `dest.below()` (与 LMA A* tryBridge aux=dest.below 完全一致) — 执行 = **放好桥块 → moveTowards 走过去 → 脚到 dest 才 SUCCESS**; MovementPillar SUCCESS = `playerFeet().equals(dest) && blockIsThere`
- **PLACE 完成判定 = `aux 已实心 && 脚格 == dest`** + 每 tick 导航 dest (BehaviorUtils): 桥 → 放好走上; 柱 → 跳起放脚格 → 落回站上 dest (放置无实体碰撞, 女仆上升穿过/下落撞顶, 窗口 = 全滞空期)。修正了放置成功即推进导致的竞态 (下一柱步把女仆将落的格提前填掉 → 站不上 dest → 卡死)
- **纯块放置链** (`LmaPlayerSimulator.simulatePlaceBlock` + `FakePlayerInteract.placeBlock`): 仿 maid_useful_task `MaidUtils.placeBlock` (BlockHitResult → UseOnContext → onItemUseFirst → PASS → useOn), **无 interactEntity 扫描 / 无 RightClickBlock 事件** — 参考源码 (maid_useful_task / Baritone processRightClickBlock) 放置均走纯块链; 原链 interactEntity 扫 target 格排除假人但**不排除女仆** → 女仆站点击格时放块变交互女仆
- **findSupportFace 还原** (去 v79.19b 加的 maid 跳过 — 纯块放置无抢占; 桥 aux=dest.below 侧壁 = 女仆脚下块, 本就不冲突)
- 保留: 无状态每 tick 决策 (柱 onGround→jump / 滞空放; 桥站立放) / 防随机走 (keepAlive + 导航目标) / borrow/restore tick 内闭环 + 诚实消耗 extractItem / 成功复查 / FAILED 气泡 600t 节流 / A* 规划层未动
- 验证: 双节点编译 / 全测试 / gametest 6/6 ×2 / jar 复制 1.21.1 mods / 游戏内人工验证 (搭柱上高/1 格宽沟/深坑搭桥 — 观察: 每格桥放完走上再放下一格, 不原地连放, 无气泡刷屏)

## 0.9.8 (2026-08-06) — v79.18 哈气 YSM 动画 + 通用动画文件同步

### v79.18 Added (2026-08-06, 哈气 YSM 动画 + S2C 动画文件同步)

- 哈气进入 LOOK 经 `AnimExecute.execute(INSTANT, "haqi")` 播放 — **双通道分流** (AnimExecute 内建, 自动 seq + LmaAnimSyncMessage 数据包同步): YSM 模型 → playRouletteAnim (动画须在 YSM 模型包内同名 "haqi"); TLM geckolib 模型 → ISS 注册的 haqi.animation.json (已加进 StartupLoader ANIM_PRESETS, 客户端启动注册)。onCleanup `stopRoulette` + `BrainHelper.unfreeze` (AnimExecute fallback freezeAI=true 置 IS_PANICKING — Brain memory 闭环, 防残留)
- **通用 S2C 动画文件同步** (`AnimFileSyncPacket`, forge ID 9 / neoforge `anim_file_sync` 纯 S2C 单 TYPE): 专用服务器玩家加入 → `pushAllTo` 全量推送 config/animations/*.animation.json → 客户端校验 (文件名白名单/禁路径遍历/512KB 上限/JSON 合法) → 落盘 → `StartupLoader.reload` + `DynamicAnimationResources.reload` → `AnimationResourceRegistrar.remergeAll` 热合并进 TLM geckolib ISS AnimationFile (启动时缓存 mutable 引用)
- 事件: forge/neoforge 各 `AnimFileSyncEvents` (@EventBusSubscriber + PlayerLoggedInEvent)
- **动画机制文档**: `docs/design/animation-playback.md` — 双通道总览 (TLM ISS FULL/INSTANT + YSM 轮盘) / 播放链 / 注册与注入 / 停止不变量 / IO 原语清单 / YSM 兼容组件 / 错题 #72-77; 死代码清理: compat/ysm/YsmCompat (v73 旧版无引用) + vanilla/output/ysm 空目录
- **FULL YSM 分流修复 (错题 #78)**: `AnimExecute.executeFull` 曾缺 isYsmModel 分支 — YSM 模型女仆走 FULL 也进 TLM ISS 通道 (YSM 渲染不吃) → 无动画; 修 = executeFull 加 YSM 分流 (playRouletteAnim + YSM_ROULETTE)
- **YSM 动画注入 (方案 A, 裁定)**: `YsmAnimInjector` — 客户端启动 (neoforge 构造器 / forge commonSetup) 幂等合并 assets 的 haqi.animation.json 进 YSM 默认模型女仆动画 `config/yes_steve_model/builtin/default/animations/tlm.animation.json` (已有 key 跳过不覆盖; 失败仅日志; YSM 还原后自愈) — 使 `playRouletteAnim("haqi")` 在 YSM 模型上可播 (wiki 实证: YSM 2.4+ tlm 动画定位, 模型包加载时读取, 无运行时注入 API)
- **修复 (实测)**: neoforge `@EventBusSubscriber(GAME)` auto-scan 对 `DefaultGeckoAnimationEvent` 失效 (TLM "Model loading time" 日志有、LMA "注册 N 个动画" 缺失、cachedIISSFile 恒 null) → 入口构造器**手动注册**到 GAME/FORGE 总线 (TLM 1.5.3 jar 反编译实证 post 到 `NeoForge.EVENT_BUS`)
- **崩溃修复 (实测 14:42)**: MOD 总线注册 `DefaultGeckoAnimationEvent` 抛 `IllegalArgumentException: bus only accepts subclasses of IModBusEvent` → mod 构造失败连锁崩 (Sodium 跟着炸). 删除所有 modBus 注册 — **neoforge 普通 Event 只能注册 GAME 总线**; 文档 "Forge MOD 总线" 为 forge 时代旧说法, 不适用
- 验证: 双节点编译 / 全测试 (新增 AnimFileSyncPacketTest 4 校验测试) / gametest 6/6 ×2 (lmaHaqiHit 改走 MOVE→LOOK 转换, 断言 TLM 分支 lma_anim 键)

## 0.9.6 (2026-08-05) — v79 任务管线 GameTick 集中管理

### v79.17 Added (2026-08-05, 哈气概率挥击)

- 哈气 LOOK 期间按概率 (hit_chance 默认 0.3) 延迟 15t 挥击目标一下: `maid.swing(MAIN_HAND)` 挥击动画 + `CombatOutput.damage` (mobAttack 真实受击链) + 原版 `PLAYER_ATTACK_SWEEP` 挥击音
- 伤害固定配置 `hit_damage` (默认 1.0 = 一点血, 不致命, 裁定) — 不走武器伤害 (`doHurtTarget` 全链含附魔/横扫, 不适用)
- 挥击状态存 `lma_pl_haqi` compound `hit_ticks` 键 (倒计时 → 0 执行 → -1 防重复), onCleanup 整体清除闭环
- TLM 实证: 女仆打女仆不引发反击 (DefaultMonsterType FRIENDLY 排除 TamableAnimal, canAttack 恒 false); 被动目标女仆 PANIC 活动 ~5s 自动恢复
- 配置: `passive.toml` haqi 段 + 全局设置 GUI 哈气分类 (挥击概率/挥击伤害 2 条目)
- 验证: 双节点编译 / 全测试 / gametest 6/6 ×2 (新增 lmaHaqiHit — 构造 LOOK+hit_ticks=1 驱动 tick 断言掉血且仅一次)

### v79.11 Fixed (2026-08-05, 实测 4 bug)

1. **跑步女仆随机走动/跟玩家**: running_belt tickRunning 加原地锚定 (NavigationUtil.keepAlive — WALK_TARGET 原地) + setHomeModeEnable(true) (防跟玩家); cleanup 恢复
2. **哈气永不触发 (概率 1.0 也无声音)**: 根因 = onSignal 走 MAID_NEARBY 信号, 但 EnvSenseBroadcaster 被 ENVSENSE_ENABLED (默认 false) 门控 → 无广播. 修复 = 新 HaqiTrigger 独立触发 (TaskTickHandler 每 20t 直接扫 2 格内女仆 → 概率 → submitPassive), onSignal 保留双通道
3. **假人被另一魂符误销毁**: onMaidRestored BINDINGS 遍历经背包石板键匹配, 放 A 魂符时背包有 B 石板键 → 误匹配 B. 修复 = PD lma_companion_uuid 直查优先 (精确), BINDINGS 遍历降为无 PD 键 fallback
4. **挖矿不挖 (头上的矿/没跑酷)**: 根因 = moveTo 用 NavGoal.standOn(next) — 目标矿是固体方块, standOn 要求站上目标格不可达 → 所有寻路模式规划失败. 修复 = NavGoal.interact (相邻格含上下, 目标格 sacred) — 跑酷/挖路生效 + 挖头上矿
- 顺带硬编码收敛: ChainHarvestExecute (TASK_WOOD/TASK_ORE/TICKS_PER_SECOND/SCAN_BUDGET_DIVISOR) + LmaMagicCastingProvider 动画键 → TaskKeys + 开发路径注释清理
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包 / 部署

### v79.10 Added (2026-08-05, running_belt 顺带摇曲柄)

- `CrankService.findCranks(level, center, range, max)` — 螺旋序收集 ≤max 个 (findCrank 参数化)
- `RunningBeltPipeline.tickRunning` 尾部: 周围 2 格内曲柄 (最多 2 个) 顺带摇 (turn) + 每 20t swing — 发电上报式不中断 (实证: isMaidOnBelt 只查 Y, addSurfaceMovement 主动上报)
- 无新状态/无新 pd 键/无导航 — 最小改动
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包

### v79.9 Added (2026-08-05, 哈气被动任务)

- 新 `task/pipeline/sense/HaqiPipeline`: 2 格内其他 maid → MAID_NEARBY 信号触发 → 概率掷骰 (10% 默认) → 锁定目标 submitPassive → MOVE (导航到她旁边 1 格) → LOOK (看着她 + 随机播放音频) → 总时长 = 基础 (60t) + 音频实际时长 → cancelPassive; 目标消失/远离 → 放弃
- **互斥**: TaskDispatcher.submitPassive (哈气运行中拒其他被动) + submit (哈气运行中拒主动任务)
- 音频: 10 个 ogg 导入 (ha_1-5 哈气音 1.1-2.2s + laowu_1-5 老五音 4.5-12.8s) + LmaSounds 注册 + sounds.json — **时长表为 ogg granule 实证** (ha 22-45t / laowu 91-256t); 命名非 maid* 前缀 (TLM playSound 音效包路线实证)
- 配置 (PassiveTaskConfig haqi 段 + Cloth"哈气"分类): ENABLED (默认关) / CHANCE (0.1) / DURATION_TICKS (60) / VOLUME (1.0)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包

### v79.8 Added (2026-08-05, 寻路模式配置 — 子任务界面 + 全局)

- 新 `config/PathingModes` — 4 档寻路模式 → PathConfig 映射: safe (纯走绕, 默认) / parkour (只跑酷) / bridge (跑酷+搭桥搭柱) / explorer (跑酷+搭+挖)
- `ActiveTaskConfig` pathing 段: `PATHING_DEFAULT_MODE` (全局默认, "safe") + `PATHING_COLLECT_MODE` (采集任务覆盖)
- Cloth UI: ClothSettingsScreen 新"寻路"分类 (startEnumSelector) + TaskSettingsScreen collect_wood/ore 分支加"寻路模式 (覆盖全局)"
- `ChainHarvestExecute.moveTo`: 采集移动段换 PathingApi.tickPath (模式分级) — FAILED → 气泡"无法到达目标" + 停留重扫; 无 fallback (裁定)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包
- 坑: NavGoal.standOn 需 P3 (BlockPos 转换); cloth 无 startCyclicButton (11.1.136 实证) — 用 startEnumSelector

### v79.7 Changed (2026-08-05, 全部状态机化 + 寻路 API 补全)

- ArmTransfer/ChainWood/ChainOre/BlockInteract 全转 TaskStateMachine + 状态处理器表 + StateCtx + API 组合 (示范模式固化)
- `PathingApi` 补 4 入口 (规划/执行分离): findPath(maid)/hasPath/tickPath/clearPath — 管线流程 = validate 预检 (无路气泡) + tick 执行
- TaskRegistry 静态 executor 调用修 (TaskStateMachine 实例方法)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2

### v79.6 Removed (2026-08-05, FSM 栈全砍 — 裁定: 状态机自己定义 + impl 也能用, FSM 无实际意义)

- 删 task/fsm/ 18 (FsmPipeline/FsmExecutor/FsmTaskFactory/FsmDef×4/FsmCheck/FsmCheckRegistry/CfgOverridesCache/check 6) + task/script/ 2 (StepCursor/CompiledAction) + task/action/ 33 (28 动作 + TaskAction/TaskActionContext/TaskActionRegistry/ParamSpec/ParamValidator) + task/condition/ 11 + TaskDef + core 链 6 (ConditionCache/StaticPreEvaluator/ConditionOperator/ActionStep/ConditionRegistry/spi.condition) + DocGenerator (条件文档随栈死)
- 接线: LmaRegistrar (3 registerAll + DocGenerator 调用删) / LmaCommand (FsmPipeline 调试分支删) / LMAT (registerAction/registerCondition 删 + imports) / network specs 通道删 (ReplyTaskConfigPacket 重写 + RequestTaskConfigPacket specsOf + LmaTaskConfigContainer.updateSpecs)
- 测试删 18 (fsm 8 + script + cache + action 5 + RegistryTest + ActionStepTest + ModelTest + LMATTest)
- **动作语义吸收进 API (6 处)**: SenseApi.findBlockNearest (find_target) / SenseApi.healthRatio (wait_until_maid_health) / FakePlayerInteract.rightClick range 重载 (use_block) / MovementOutput.teleportByMode (teleport) / VisualOutput.playAnimFull (play_anim) / MaidStateWriter.repairItemWithXp (repair_item)
- 保留: TaskStateMachine (ArmTransfer) + 12 实例管线 + 能力 API (SenseApi/PathingApi/NavigationUtil/ContainerOutput) + RuleContext/IAction (vanilla)
- 坑: EquipmentSlot.Type 1.21.1 不存在 (显式 4 甲槽) / 宽 sed 误删闭合 (git checkout 恢复 + 重做) / sed 删方法调用留悬挂参数行 (initServer) / ParamSpec 网络协议死字段 (v77.4 恒 null 标注)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包

### v79.5 Changed (2026-08-05, 一次性管线 API 填充 — 裁定: 拿 API 填管线, 管线=参数+API)

- 新 `api/nbt/NbtCodecs` — 双平台 BlockPos↔NBT 编解码 (格式兼容零迁移; 替代 ArmTransfer 2 + BlockInteract 3 处样板)
- 新 `api/navigation/NavigationUtil` — navigateTo/arrived/keepAlive 三件套 (替代 ArmTransfer/ChainHarvest 内联同款)
- `SenseApi.findNearestBlock` — 泛化最近目标搜索 (BlockScanner + skip 集 + 最近优先; ChainHarvest.findNearestValid 提升到 API 面)
- `ContainerOutput` 增强 — getHandler (capability 六方向, 双平台条件化) + depositItemStack/withdrawItemStack (isSameItem 溢出退还统一; ArmTransferService 第二份实现删, execute* 改委托)
- 管线改薄: ArmTransfer (编解码/导航/容器委托) + BlockInteract (3 处编解码) + ChainHarvest (导航/keepAlive 委托)
- 坑: stonecutter 条件 import 块多行分支处理异常 (forge merged 丢行 — 错题 #37 家族) — ItemHandlerHelper 依赖弃用, 改逐槽 insert 循环 (双平台安全); ContainerOutput 缺 BlockPos import (全限定修)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包

### v79.4 Removed (2026-08-05, 规则引擎残留全链删除 — 裁定)

- 删 19 个 main 类: ConditionMatcher/ConditionEvaluator/CooldownManager/ParamMerger (core/engine) + ExpressionResolver/MvelBootstrap/MvelEvaluator (core/expression, MVEL 表达式) + ConditionDef/MatchMode (core/model) + RuleCondition/RuleAction/ScriptPlugin (core/annotation) + ActionRegistry (core/registry) + ClassScanner/ForgeClassScanner (注解扫描器 — 扫描目标 impl 包 v72 已删, 空扫) + ScriptPlugin/IScriptPlugin/ScriptPluginRegistry (core/spi/script)
- 删 5 测试: ConditionMatcherTest/CooldownManagerTest/ParamMergerTest/ExpressionResolverTest 整删; RegistryTest/ModelTest 手术 (留 ConditionRegistry/ConditionOperator/ActionStep 部分)
- DocGenerator 动作文档段删 (ActionRegistry 依赖; 任务动作文档 = docs/guides/task-development.md 已有)
- vanilla SubmitTaskAction/AbstractBlockInteraction 去 @RuleAction 残留标注; LmaRegistrar 去扫描调用
- **保留 (任务引擎引用链)**: RuleContext/ConditionCache/StaticPreEvaluator/ConditionRegistry/ICondition/ConditionOperator/ActionStep (FsmCheckAdapter 条件桥接) + IAction/ActionCategory (vanilla) + TypedParam/MaterialChecker/MaterialReport/LmaAnimationDef (任务引擎) + DocGenerator 条件文档
- EnvSenseBroadcaster.emit 去 ctx 死参数 (零消费; SenseApi.emit 同步 3 参)
- 验证: 双节点编译 / 全测试绿 / gametest 5/5 ×2 / 打包 0.9.6

### v79.3 Added (2026-08-05, EnvSense 补全 + SenseApi 暴露 — Numen 参考)

- 信号补全 (EnvSignal 18→21): `env:BIOME_CHANGE` / `env:STRUCTURE_ENTER` / `env:STRUCTURE_LEAVE` — WorldInfo +biomeId/structuresAt (站立点所在结构, getAllStructuresAt 零成本, 与 24000t 最近结构通道互补)
- `EnvEdgeDetector` (纯 JVM 边沿检测核心, 21 边沿从广播器剥离) + `EnvRules` (温度档/时间段纯逻辑)
- `ScanScheduler` + `ScanJob` 增强 (io 层): Tickable 契约 / ownerId 归属 / cancel() / matches 防御拷贝; TaskTickHandler 集中挂载; 女仆卸载 cancelFor 清理闭环; `ScanFilters` (雪/红石灯/水源/熔岩源谓词)
- **`api/sense/SenseApi`** (执行层 API 暴露, 仿 PathingApi): snapshot/worldInfo (O(1) 快照) / biomeAt/structuresAt (直读) / scanEntities/scanSnow/scanRedstoneLamps (同步有界) / **startScan/cancelScan/scanResults (预算化异步扫描)** / emit (事件注入) / tempCategory/timeSegment (纯逻辑)
- 预算优化: dispatch validate pass 作用域缓存 (每管线一次, 原每信号×每管线) + `EnvSenseBudget` 广播墙钟 8ms 上限 (超限跳过快照刷新, 边沿只延后不误报)
- 测试 +25: EnvRulesTest 2 / EnvEdgeDetectorTest 11 / ScanSchedulerCoreTest 5 / SenseApiTest 4 / EnvSenseBudgetTest 3 (325 全绿)

### v79.1 Added (2026-08-05, Baritone 算法移植)

- `PrecomputedData` (vanilla/pathing/): 谓词位掩码缓存 — state 级确定语义按 id memoization (位图单次计算, A* 重复扩展同类方块谓词归零; destroyTime 同缓)
- `PathExecutor` 双段路径 (B2): 剩余步数 ≤ 8 前瞻预计算下一段 (以路径末端为起点); 段末 goal 已达 → ARRIVED, 否则容差切换 next (≤4 格) 或从当前位置重算 — 原"路径到头即完成"改进为"真达目标才完成"
- 测试: PrecomputedDataTest 5 例 (语义等价/memo 计数/独立缓存) + PathExecutorTest 4 例 (纯函数判定)

### v79.2 Fixed (2026-08-05, 假人重启位置)

- 假人重启后出现在奇怪位置: 玩家登录 (LOWEST, Numen respawnAllOwnedBy 之后) → 追踪魂符 (背包石板 lma_companion 键) → 在线假人 teleportTo 玩家旁 (视线前方 2 格)。覆盖: roster 残留 .dat 旧位置复活 + 自主游走残留
- 重启后交接失效 (BINDINGS 内存态清空): onMaidRestored 无绑定兜底 — 新女仆 PD lma_companion_uuid 直查假人 → 销毁 + 清石板键 (不用背包扫描防多假人误伤)

### Added

- `GameTickPipelineManager` (v79): 主动/被动每 tick 驱动集中管理 — 心跳节流 (FLOW_TICK 每 20t 一写, 原每 tick) + 看门狗容忍度 (有效超时 [timeout, timeout+20]) + 被动预算轮转
- `TaskPipeline.priority()` (默认 0): 提交冲突优先级策略 — 新任务严格更低 → 拒绝 (气泡节流), 等/高 → 抢占 (树内任务零行为变化)
- `PassiveTaskConfig.PASSIVE_TICK_BUDGET` (默认 2, 0=不限): 每女仆每 tick 最多执行的被动管线数, 超预算环形轮转 (PassiveRotation 确定性零状态)
- `WatchdogMath` / `CfgOverridesCache` / `PassiveRotation` 纯 JVM 类 + 测试
- gametest +2: lmaPriorityConflict / lmaPassiveBudget (双节点 5/5)

### Changed

- `TaskTickHandler` 变薄: 双循环 → 单次实体遍历; 被动清单每 level hoist (TaskRegistry.passiveTasksList 缓存)
- `FsmPipeline`: 预计算 plKey/cfgKey + 游标 finished 后 reset() 复用 + 配置覆盖视图缓存 (稳态零分配)
- 死代码清理: TaskExtraData 删; ScriptedPipeline/JSON 平台死注释 6 处修正

### Fixed

- `super.handleConfigAction()` 接口默认方法调用 → `TaskPipeline.super.handleConfigAction()` (plain super 不搜接口 — 编译坑)

## 0.9.5 (2026-08-05)

### Added

- 近距离寻路 API (v78, vanilla/pathing/ 纯 JVM + 执行器): PathWorld/NavGoal/MovementHelper/AStarPathFinder (25 移动集: 走/挖/搭桥/搭柱/斜走/保守跑酷, 危险硬禁: 岩浆/火/斜角/冲线) + PathingApi.findPathSafe/findPathExplorer
- PathExecutor (v78.2-3): WALK/MINE/PLACE/JUMP 逐 tick 执行; 渐进破坏 (swing + getDestroyProgress 累积 → destroyBlock, maid_useful_task 模式); sweep 全量驱动 (TaskTickHandler)
- navigate_to FSM 动作 (28 动作, 条件阻塞; mode=block/near/interact + 能力开关)
- AI move_to 工具升级 (v78.3): getNavigation → PathExecutor; mode=safe (不破坏方块) / explorer (挖/搭/跑酷)

### Changed

- 跑酷默认开 (保守 1-2 格坑 + overshoot 硬防)
- 假人全局设置 GUI (v77.9): ai_control 任务设置屏 + 随机台词气泡开关/间隔/随机语音开关

## v78 Phase 1 (2026-08-05, 并入 0.9.5)

## 0.9.4 (2026-08-04)

### Added

- 假人侧 YSM 命令通道 (YsmCommandChannel, v77.8): `/ysm model set` / `/ysm model disable` / `/ysm anim play` — 镜像 YsmOutput IO 面, 混淆版 YSM 2.6.5 全支持 (roamingVars 无服务端命令不镜像)

### Changed

- applyMaidModel 命令分支提取至 YsmCommandChannel (NumenMaidBridge, 双通道解耦)
- 变身前置提示: OpenYSM 硬前置 → 任意 YSM (混淆版走命令通道)
- docs/architecture/compat.md + ARCHITECTURE §11.5 同步双通道 (漂移修复)

## 0.1.0

### Added

- Initial reusable multi-loader template.

## Types of changes

- `Added` for new features.
- `Changed` for changes in existing functionality.
- `Deprecated` for soon-to-be removed features.
- `Removed` for now removed features.
- `Fixed` for any bug fixes.
- `Security` in case of vulnerabilities.

## 0.71 (2026-08-03) 假人石板化 + Create 双平台

### Added
- v75 石板化假人桥: ai_control 设置 GUI "变成假人" 按钮 → 假人唯一主体 (固定 UUID 每女仆独立, YSM 模型自动继承 via OpenYSM DataAttachment, 状态同步) → 女仆带全背包收 TLM 石板 (idle 存石板防循环)
- 放石板交接: 假人销毁 (dismiss + roster 清理) + 物品爆地
- LLM provider/voice 继承 (SHELVED 广播) / 随机台词+语音 (仿 TLM RandomEmoji, 读 TLM MaidConfig)
- 前置门控: ai_control 注册需 Numen; 变身按钮需 Numen + OpenYSM 2.6.6+ (开源版)
- Create 任务 running_belt/maid_assembly 双平台化 (1.21.1 neoforge, 18+ 处 API 适配)
- 界面: 旋转全景背景 (1.21), 透明化, ai_control 中文翻译

### Fixed
- 收女仆崩溃 (1.21 ItemStack.save 禁空编码 → saveItem isEmpty 检查)
- 调试按钮主菜单崩溃 (PacketDistributor.sendToServer connection null → Sender 防御)
- 假人头顶气泡不显示 (旧 .dat INVISIBILITY effect → 强制清除)
- 假人残留登录复活 (despawn → dismiss roster 清理)
- 任务树模糊背景 (1.21 renderBackground → 覆写 + 旋转全景)

## 68.0.0 (2026-08-02) 架构重构
- 规则引擎残留裁撤: 27 个纯转发事件订阅者删除 (TlmEventAdapter 2 订阅者), RuleEvent/MaidInteractBridge/孤立事件删除, 任务注册与规则引擎解耦
- 死代码删除: FlowTask/TaskFlowGraph/AbstractTaskCondition/ServerTaskQueue/ForgeTaskQueueBridge/FlowTaskData 旁路
- 共享基类: LmaFlowTaskBase + LmaTaskConfigContainer 容器契约上提
- tlm-ref 参考源集删除 (8.2MB)
- Parchment 映射 (neoforge 节点, TLM 同款 2024.11.17-1.21.1)
- ⚠️ /lma task flow 调试命令移除 (TaskFlowGraph 死代码)
- 测试: 157 单元测试 + gametest 任务生命周期测试

## 0.71.0 (2026-08-03) Numen 假人桥 (v74)

### Added
- Numen 假人桥: 女仆 ai_control 开启 → 生成 NumenPlayer 假人 (owner=女仆主人) → **Numen 全套 LLM+工具驱动** (零 LMA 工具编写)
- 动作镜像: 假人攻击/交互/挖掘事件 → 女仆 swing 动画 (左手攻击/右手交互)
- 视角同步 (20t 节流) + 移动跟随 (寻路 20t 节流, 停靠 ≤2.5 格) + 背包单份同步 (女仆=唯一真源)
- Numen 共存检测 (NumenCompat, 未装零开销)
- 全玩家隐形: 客户端 RenderPlayerEvent.Pre 取消渲染 (身体+手持物品全隐; 20t 广播假人 UUID 集识别)
- 成就摘监听: `PlayerAdvancements.stopListening()` (假人背包拷贝不再触发 story/diamond 等成就)
- 面板删除拦截: CompanionLifecycle.onRemove → dropAll 副本去重回收进女仆背包 + 不重生标记 (任务重开恢复)
- AI 操控任务设置: LLM 模型/声线名称 (TLM 任务设置 GUI + 详细设置→任务自定义全局默认, 空=不绑定) → 桥广播 → owner 客户端按名绑定 Numen ProviderLibrary/VoiceLibrary
- 引擎配置动作 ACTION_SET_STRING=4 (字符串赋值)
### Changed
- 任务树渲染双平台修复 (withStyle 颜色, 1.21 灰屏问题)
- Cloth Config 软依赖 (未装提示不崩)
- 修复: 假人 noPhysics 穿地掉落重生循环 (Entity.move 直接 setPos 语义) → 去 noPhysics + 生成在女仆头上一格

## 0.70.0 (2026-08-02) AI 操控 (v73)

### Added
- AI 世界操作工具 10 个 (TLM AI 环 + LMA IO): move_to/mine_block/collect_items/interact_block/interact_entity/melee_attack/get_self_status/switch_lma_task/scan_blocks/wait_ticks
- 主动任务 `ai_control` (AI 操控): 权限门控 — 开启后女仆 AI 对话可指挥世界操作 (关闭即收回)
- Numen 共存兼容 (ModList 检测 + 工具模式参考)
- gametest lmaAiControlGate (门控闭环)

## 0.69.0 (2026-08-02) 引擎重构: 规则引擎 → JSON 任务插件平台 (v72, 5 Phase 完结)
### Added
- JSON 任务定义: `config/littlemaidmoreaction/tasks/*.task.json` (KubeJS 风格数据驱动, 一文件一任务; 11 内置预设)
- 任务插件平台: TaskDef/TaskStorage/TaskPresets/TaskSignalIndex/TaskScreener (纯 JVM 筛选核心)
- 条件库: 10 个 ICondition (damage_type/would_lethal/maid_has_shield/target_holding_item/maid_has_weapon/is_combat_task/is_owner_target/owner_has_attack_target/is_tamed/owner_holding_item)
- 动作库: 16 个 TaskAction (全委托 IO 原语) + StepCursor 脚本管线 (wait/random/异常容错)
- 事件桥: 5 信号 (maid_attack/maid_hurt_target_pre/maid_interact/maid_tick 200t 节流/maid_harvest_crop) + 取消通道 (cancel_event → setCanceled)
- 任务筛选服务 TaskScreeningService.fire (索引→筛选→提交→游标→取消判定)
- 信号泛化: 统一 String 信号 id (event:/env: 前缀) + EnvSenseBroadcaster.emit 事件信号入口
- `/lma task reload` JSON 任务热重载命令
- gametest 4/4 (新增脚本被动任务全链 + 事件取消)
### Changed
- 信号链路接口: TaskPipeline.onSignal 第 3 参 String (破坏性)
- 11 预设 eventId 归一为 event: 前缀 (与信号契约一致)
- CooldownManager 冷却键 lma_tcd_<id> (规则引擎 lma_rule_ 前缀删除)
### Removed
- 规则引擎全套删除: RuleEngine/ActionPipeline/GroupBuilder/ParallelGroup/ITickScheduler/TickScheduler/RuleIndex/MaidRuleIndex/MaidRuleStorage/RuleActionStorage/RuleTracer/DebugPresets/RuleDef + 绑定测试 + assets/rules 死资源
- 配置项: 规则引擎总开关 (CUSTOM_RULES_ENABLED); DEBUG_MODE 保留
- /lma rule + /lma trace 调试子命令
- 规则 JSON 模板文档 (rule-template.md) + TLM skill 复制 (lma_rule_system)
- ⚠️ 旧 config/rules 目录不再加载 (自然退役, 文件不动)
### Fixed
- 节流时间戳防溢出 (PD 跨 session 锁死) / 脚本游标启动缺失补偿 / 信号路由顺序
