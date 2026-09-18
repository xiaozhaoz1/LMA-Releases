# Little Maid More Action (LMA)

车万女仆 (Touhou Little Maid) 附属模组 — 管线 + API 任务系统 (代码注册任务/连锁采集/环境感知/哈气动画/女仆 GUI/缓存体系/调试选区)。

Stonecutter 多版本架构: **forge 1.20.1** (0.9.72) + **neoforge 1.21.1** (0.9.72)。

当前形态: **管线 + API** — TaskRegistryManifest 规格表注册 **主动 21** (双平台同表: ALWAYS 13 + Create 6 + Numen 1 + CBC 1; v79.62.2 +dam_fill 填坝排水) + **被动 5 管线 + 纯触发 3** (三形态: 纯触发 PassiveDispatcher / 跨 tick 独立心跳 / GMPM 真管线; v79.62 铲雪删 — TLM 原版清雪覆盖, 天气检出保留给 LLM 对话) + LMAT 扩展点 + 寻路 (走路全 TLM + 头顶挖穿 digUp + 危险堵护 + 卡方块自救; v79.57 脚下挖穿退役) + **缓存体系 (StructureScanCache 静态层 / EntityScanCache 区块实体 / BlockPatternCache 单区块方块 / WorldInfoCache 世界分区)** + 独立域 (防御塔 garage_kit / 五子棋判赢 / 任务树)。**数字一律以 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 顶部实测基线表为准**。

## 快速开始

```bash
# 编译 (缓存不可靠, 必带 --no-build-cache --rerun-tasks)
./gradlew :forge:1.20.1:compileJava --no-build-cache
./gradlew :neoforge:1.21.1:compileJava --no-build-cache

# 冒烟 (游戏窗口, 看 run/logs/latest.log 0 ERROR)
./gradlew --configure-on-demand :forge:1.20.1:runClient --console=plain
./gradlew --configure-on-demand :neoforge:1.21.1:runClient --console=plain

# 单元测试 (forge 节点; 类/用例数见 ARCHITECTURE 基线表)
./gradlew :forge:1.20.1:test --rerun-tasks

# gametest 双节点 (跑前必清 run/gametest/world; 数字见基线表)
./gradlew :forge:1.20.1:runGameTestServer --no-build-cache
./gradlew :neoforge:1.21.1:runGameTestServer --no-build-cache

# 打包 (javadoc 中文 GBK 解析炸 → 一律 -x javadoc)
./gradlew :forge:1.20.1:jar :neoforge:1.21.1:jar -x javadoc --no-build-cache
```

## 功能

- **种菜区域制** — farm (v79.62; **v79.63.23 起改存女仆 NBT** `lma_cfg_farm.regions` 并记 dimension — 老 `config/littlemaidmoreaction/farm_regions.json` 仅**一次性导入**后改名 `.imported`): 木棒框选区域 + GUI 注册 (作物/收获模式/种子源箱/收获箱) → 女仆收到成熟作物 (破坏/右键 FakePlayer/茎摘果实/垂直顶端四通道) + 按区域种指定作物; 区域 Y 轴自动 ±1 扩 (耕地层)
- **填坝排水** — dam_fill (v79.62.2): 大海排水前置 — 两模式独立 (填坝=外圈 4 方向筑重力方块墙 [沙/砾石/铁砧自落填充, 检测列堆顶到标记层+3] / 排水=按排从墙边往里 [一排横穿所有区块, 液体→空气+粒子+舀水声]); cfg 只存标记配置, 游标全 PD 内存; 任务期间 HOME + 水下呼吸
- **挖空置域** — void_excavation: 区块工作制 (认领池 + ChunkWorkArea restrict 防拉回) + 无导航模式传区块中间直接挖 + 液体相邻扫描/提前空气化 + 矿必须裸露才挖 (6 邻面至少 1 面空气, 脚下 >2 格深矿扫描排除) + 导航重试 (5s 尝试→2s CD→2 次→跳过集 10s)
- **刷子** — brush (v79.62): 女仆找周围可疑沙/砾石 (BrushableBlockEntity) 走过去, 每 10t 用 FakePlayer 模拟刷扫, 掉落进背包
- **营火** — campfire: 走到营火旁取熟食 (CampfireBlockEntity 四槽), 满背包/无熟食时节流等待
- **连锁采集** — collect_wood / collect_ore: 连块脉 BFS + 蓄力整脉破坏 + 工具判断 (镐/斧/铲按目标方块) + 换工具不丢旧工具
- **寻路** — 走路全 TLM 原版导航 (maid_useful_task 模式): 头顶挖穿 (深度 6) / 危险矿堵护 (6 侧液体堵方块) / 卡方块自救 / 跳过集 TTL + 寻路进展守护 (nav_progress_guard)
- **哈气互动** — 对女仆/主人哈气 + YSM 动画 + 对主人专用语音 + 表情气泡 (5s 防刷屏) + 概率挥击; **哈气运行中完全独享女仆 tick (其余被动/新任务冻结)**
- **任务气泡 API** — MaidChatBubbleApi 5 类 (进度替换式/失败超时 600t 节流) + 任务步骤气泡
- **女仆 GUI** — 独立女仆列表屏 (服务端全维度扫描, 3D 预览) + 属性屏 (16 属性 + LMA 参数按钮 → TLM 任务配置) + 模组主界面 (全景背景) + **其他设置屏** (五子棋判赢开关)
- **任务树 + 成就感知** (v79.63) — 原版成就 JSON 运行时读取 (自动跟随版本) → FTB Quests 风格成就树屏 (章节/自动布局/进度着色); `AdvancementSenseApi` 主人成就完成 → 女仆反应 (通配/精确注册, **待用户写反应验收**)
- **环境感知 (被动系统)** — 200t 扫描 **6 个边沿信号** (SNOWING/WEATHER_CLEAR/DARKNESS/MAID_NEARBY/FESTIVAL_ENTER/RARE_BIOME; SNOWING/WEATHER_CLEAR 检出保留给 LLM 对话 — v79.62 用户裁定) → 被动 5 管线 + 纯触发 3:
  - **火把** (DARKNESS → 副手灯, 亮且无怪自动收回闭环, 无灯 300t 重试)
  - **哈气** (MAID_NEARBY 边沿 + 独立触发, FSM 动画/语音/覆盖)
  - **自救** (MLG 摔落自救 + 卡住脱困, 不掉血预触发)
  - **酒狐奶 / 探险家地图** (被动管线 jiuhu_milk / explorer_map)
  - **结构感知 / 节日 / 稀有群系** (纯触发气泡, 缓存文案/当天首收去重)
  - 分层: 纯触发 (PassiveDispatcher 四道闸) / 跨 tick (独立心跳 + 管线内节流自持) / 真管线 (haqi+self_rescue); 铲雪 v79.62 移除 (TLM 原版清雪覆盖, 用户裁定); 温度适应 v79.62.5 移除 (温度改由 TLM `getAtBiomeTemp` 提供)
- **节日日历** — 现实日期检测 (LocalDate) + festival.json 可编辑节日表 (6 内置) → 节日气泡
- **酒狐奶** — 酒狐奶桶/野生奶 + 挤奶三态 + 饰品效果 + **替乳合成 (双平台牛奶标签 forge:milk + c:milk [1.21.1 单数 tags/item 目录] + 自注册蛋糕配方 [1.20.1 recipes/复数 + result.item / 1.21.1 recipe/单数 + result.id], 原版配方零覆盖)** + **清负面 (clear_negative 配置)**
- **防御塔** — defense garage_kit (v79.62.3): 独立域 (非任务) — 塔方块/BE 继承 TLM TileEntityGarageKit + 配置屏 + 索敌走 EntityScanCache; 主人不在线不攻击; 弹幕模式 (御币双倍耐久 / 箭耗 1)
- **五子棋判赢** — gomoku (v79.62.3): 女仆开关 (PD `lma_gomoku_instant_win`) 开着时, 玩家新局空手点棋盘 → 预置 4 黑子让玩家这手成五连, 好感/胜场/成就走 TLM 原链路
- **女仆图鉴** — 女仆击杀计数 (PD lma_codex) + 图鉴书物品 (右键开击杀图鉴界面)
- **兼容** — Create 6 生态 (曲柄/动力/压片/搅拌/跑步机/装配) + CBC 火炮装填 (双平台) + Numen 假人桥 + YSM 动画注入 (config/animations/ 零代码接入) + **PatPat 抚摸 (好感+1/爱心/对主人语音/气泡, 600t 节流)**

## 工具

- **选区调试 (v79.61x)** — 木棒潜行+右键标记起点→终点 → 世界内线框渲染 (起点黄/终点红/区域青 12 边 + 3D 网格 + HUD 尺寸 WxHxD) + **缓存区块边界叠加** (BlockPatternCache/EntityScanCache 覆盖区可视化); 第三击清除

## 缓存体系 (v79.61x)

- **StructureScanCache** — 结构区块缓存 → **静态层** (结构位置生成后不变, 存在即有效) + 2ms 预算摊薄螺旋
- **EntityScanCache** — 区块级实体缓存 (TTL 200t + grace 40t + 漂移门限 3.5 格 + 主人区块预热; ≤2 直扫 / ≥3 共享)
- **BlockPatternCache** — 单区块方块模式缓存 (热 100t/水 100t/容器 200t, 地表高度窗, 粗匹配+细筛防脏读; v79.62 雪缓存删 — 清雪移除)
- **WorldInfoCache** — 世界信息分区缓存 (per-dim 天气/时间每轮 1 次 + per-chunk 温度/亮度/biome 同区块共享)

## 布局

- `common/` — 平台中性代码 (**383 java main** + 68 测试类, `//? if 1.20.1` 条件化; 逐包实数与数字真相源见 ARCHITECTURE 基线表)
- `forge/src/` — Forge 专属 (LmaForgeClientEntry + create/cbc compat)
- `neoforge/src/` — NeoForge 专属 (LmaNeoForgeEntry/payload 网络 + Numen 石板桥)
- `versions/<mc>/gradle.properties` — 版本节点唯一真相源 (project.version = 正式版本号)
- `libs/` `libs-maven/` — 本地 jar 依赖 (不入库)
- `build-logs/` — 构建/测试日志落盘处 (编译/单测/gametest 各一份)
- `scripts/` `resources-tlm-reference/` — 排障脚本与 TLM 参考资源 (非构建必需)
- `fabric/` — 空目录 (Stonecutter 脚手架预留, 当前无 fabric 节点)

## 支持节点

- `1.20.1-forge` (MDG legacyforge)
- `1.21.1-neoforge` (MDG, Parchment 2024.11.17 与 TLM 同款)

## 文档

- 架构: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (顶部实测基线表 = 唯一数字真相源)
- 开发手册 (低配 API 模型): [docs/AI-CODING-GUIDE.md](docs/AI-CODING-GUIDE.md) + 工具类目录 [docs/TOOL-CATALOG.md](docs/TOOL-CATALOG.md)
- 错题集: [docs/lessons-learned.md](docs/lessons-learned.md) (#150-359; 编号与条数见其头行 + 注释规范)
- 文档导航: [docs/README.md](docs/README.md)
- 寻路设计: [docs/design/pathing-api.md](docs/design/pathing-api.md)
- 能力 API 指南: [docs/guides/api-guide.md](docs/guides/api-guide.md)
- 变更历史: [changelog.md](changelog.md)

## 关键约定

- 修改 common 后**必须双节点编译验证** (条件化分支各自独立)
- 版本号: `versions/<mc>/gradle.properties` project.version + 双节点 mods.toml 手写同步; 改后必须清 Stonecutter merged 缓存 (common/versions/*/build) 重打
- neoforge 资源改动后 `:neoforge:1.21.1:copyResourcesToClasses --rerun-tasks` (FML 从 classes 读 mod)
- 专用服务器兼容: 主类字节码不可引用客户端类 (Screen 等) — 客户端注册走独立入口
- 1.21.1 数据包差异: pack_format 48, 结构路径 `structure/` (单数), 标签目录 `tags/item` + 配方目录 `recipe/` + 配方 result `id/count` (1.20.5+ 单数化, 资源差异按平台目录拆分)
- 删除/移动 common 资源后必须 clean 版本节点 build 再打包 (stonecutter 合成缓存增量残留, #246)
- TlmEventAdapter 仅 2 订阅者 (InvariantTest 守护) — 新事件桥走独立类
- 静态缓存管理: maidId key 的静态 map 终结即清 + 实体卸载清理 (EntityCleanupListener) + 维度级缓存 clearDimension

## Contributors

- [xiaozhaoz1](https://github.com/xiaozhaoz1) — 项目作者
- DeepSeek — AI 编程辅助 (代码生成 / Bug 调试 / 文档)

## License

MIT
