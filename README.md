# Little Maid More Action (LMA)

车万女仆 (Touhou Little Maid) 附属模组 — 管线 + API 任务系统 (代码注册任务/连锁采集/环境感知/哈气动画/女仆 GUI/缓存体系/调试选区)。

Stonecutter 多版本架构: **forge 1.20.1** (0.9.63) + **neoforge 1.21.1** (0.9.63)。

当前形态: **管线 + API** — TaskRegistryManifest 规格表注册 17 主动 (双平台, forge 与 neoforge 均含 cannon_load — 2026-08-16 移植; v79.62.2 +dam_fill 填坝排水) — ALWAYS 14 + Create 6 + Numen 1 + CBC 1 + **6 被动 (三形态: 纯触发 PassiveDispatcher / 跨 tick 独立心跳 / GMPM 真管线; v79.62 铲雪删 — TLM 原版清雪覆盖, 天气检出保留给 LLM 对话)** + LMAT 扩展点 + 寻路 (走路全 TLM + 头顶挖穿 digUp + 危险堵护 + 卡方块自救; v79.57 脚下挖穿退役) + **缓存体系 (StructureScanCache 静态层 / EntityScanCache 区块实体 / BlockPatternCache 单区块方块 / WorldInfoCache 世界分区)**。

## 快速开始

```bash
# 编译 (缓存不可靠, 必带 --no-build-cache --rerun-tasks)
./gradlew :forge:1.20.1:compileJava --no-build-cache
./gradlew :neoforge:1.21.1:compileJava --no-build-cache

# 冒烟 (游戏窗口, 看 run/logs/latest.log 0 ERROR)
./gradlew --configure-on-demand :forge:1.20.1:runClient --console=plain
./gradlew --configure-on-demand :neoforge:1.21.1:runClient --console=plain

# 单元测试 (forge 节点, 474 用例)
./gradlew :forge:1.20.1:test --rerun-tasks

# gametest 48/48 双节点
./gradlew :forge:1.20.1:runGameTestServer --no-build-cache
./gradlew :neoforge:1.21.1:runGameTestServer --no-build-cache

# 打包 (javadoc 中文 GBK 解析炸 → 一律 -x javadoc)
./gradlew :forge:1.20.1:jar :neoforge:1.21.1:jar -x javadoc --no-build-cache
```

## 功能

- **填坝排水** — dam_fill (v79.62.2): 大海排水前置 — 两模式独立 (填坝=外圈 4 方向筑重力方块墙 [沙/砾石/铁砧自落填充, 检测列堆顶到标记层+3] / 排水=按排从墙边往里 [一排横穿所有区块, 液体→空气+粒子+舀水声]); cfg 只存标记配置, 游标全 PD 内存; 任务期间 HOME + 水下呼吸
- **挖空置域** — void_excavation: 区块工作制 (认领池 + ChunkWorkArea restrict 防拉回) + 无导航模式传区块中间直接挖 + 液体相邻扫描/提前空气化 + 矿必须裸露才挖 (6 邻面至少 1 面空气, 脚下 >2 格深矿扫描排除) + 导航重试 (5s 尝试→2s CD→2 次→跳过集 10s)
- **连锁采集** — collect_wood / collect_ore: 连块脉 BFS + 蓄力整脉破坏 + 工具判断 (镐/斧/铲按目标方块) + 换工具不丢旧工具
- **寻路** — 走路全 TLM 原版导航 (maid_useful_task 模式): 头顶挖穿 (深度 6) / 危险矿堵护 (6 侧液体堵方块) / 卡方块自救 / 跳过集 TTL + 寻路进展守护 (nav_progress_guard)
- **哈气互动** — 对女仆/主人哈气 + YSM 动画 + 对主人专用语音 + 表情气泡 (5s 防刷屏) + 概率挥击; **哈气运行中完全独享女仆 tick (其余被动/新任务冻结)**
- **任务气泡 API** — MaidChatBubbleApi 5 类 (进度替换式/失败超时 600t 节流) + 任务步骤气泡
- **女仆 GUI** — 独立女仆列表屏 (服务端全维度扫描, 3D 预览) + 属性屏 (16 属性 + LMA 参数按钮 → TLM 任务配置) + 模组主界面 (全景背景)
- **环境感知 (被动系统)** — 200t 扫描 **8 个边沿信号** (v79.61x 死信号同族清理; SNOWING/WEATHER_CLEAR 检出保留给 LLM 对话 — v79.62 用户裁定) → 6 被动:
  - **温度适应** (COLD/HOT → 热源/水源旁安全落点 [剔除火方块 + 落点偏移]; **触发即写 5min CD, 到火/水旁立即结束** — 不长时间停留)
  - **火把** (DARKNESS → 副手灯, 亮且无怪自动收回闭环, 无灯 300t 重试)
  - **哈气** (MAID_NEARBY 边沿 + 独立触发, FSM 动画/语音/覆盖)
  - **自救** (MLG 摔落自救 + 卡住脱困, 不掉血预触发)
  - **结构感知 / 节日** (纯触发气泡, 缓存文案/当天首收去重)
  - 分层: 纯触发 (PassiveDispatcher 四道闸) / 跨 tick (独立心跳 + 管线内节流自持) / 真管线 (haqi+self_rescue); 铲雪 v79.62 移除 (TLM 原版清雪覆盖, 用户裁定)
- **节日日历** — 现实日期检测 (LocalDate) + festival.json 可编辑节日表 (6 内置) → 节日气泡
- **酒狐奶** — 酒狐奶桶/野生奶 + 挤奶三态 + 饰品效果 + **替乳合成 (双平台牛奶标签 forge:milk + c:milk [1.21.1 单数 tags/item 目录] + 自注册蛋糕配方 [1.20.1 recipes/复数 + result.item / 1.21.1 recipe/单数 + result.id], 原版配方零覆盖)** + **清负面 (clear_negative 配置)**
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

- `common/` — 平台中性代码 (**367 java main** + 66 测试类, `//? if 1.20.1` 条件化)
- `forge/src/` — Forge 专属 (LmaForgeClientEntry + create/cbc compat)
- `neoforge/src/` — NeoForge 专属 (LmaNeoForgeEntry/payload 网络 + Numen 石板桥)
- `versions/<mc>/gradle.properties` — 版本节点唯一真相源 (project.version = 正式版本号)
- `libs/` — 本地 jar 依赖 (不入库)

## 支持节点

- `1.20.1-forge` (MDG legacyforge)
- `1.21.1-neoforge` (MDG, Parchment 2024.11.17 与 TLM 同款)

## 文档

- 架构: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (顶部实测基线表 = 唯一数字真相源)
- 错题集: [docs/lessons-learned.md](docs/lessons-learned.md) (v79.41+ 错题 #150-250 + 注释规范)
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
