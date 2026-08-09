# Little Maid More Action (LMA)

车万女仆 (Touhou Little Maid) 附属模组 — 管线 + API 任务系统 (代码注册任务/连锁采集/环境感知/哈气动画/女仆 GUI)。

Stonecutter 多版本架构: **forge 1.20.1** (0.9.23) + **neoforge 1.21.1** (0.9.23)。

当前形态: **管线 + API** — TaskRegistry 代码注册 12+ 任务 (连锁砍树/挖矿、熔炉、敲钟、搬运、右键交互、Create 系等) + LMAT 扩展点 + FsmPipeline 主动引擎 + 27 动作/7 检查/10 条件 + 寻路 (走路全 TLM + 上下垂直挖穿 + 危险堵护 + 卡方块自救)。

## 快速开始

```bash
# 编译 (缓存不可靠, 必带 --no-build-cache)
./gradlew :forge:1.20.1:compileJava --no-build-cache
./gradlew :neoforge:1.21.1:compileJava --no-build-cache

# 冒烟 (游戏窗口, 看 run/logs/latest.log 0 ERROR)
./gradlew --configure-on-demand :forge:1.20.1:runClient --console=plain
./gradlew --configure-on-demand :neoforge:1.21.1:runClient --console=plain

# 单元测试 (forge 节点)
./gradlew :forge:1.20.1:test --no-build-cache

# gametest 7/7 双节点
./gradlew :forge:1.20.1:runGameTestServer --no-build-cache
./gradlew :neoforge:1.21.1:runGameTestServer --no-build-cache

# 打包 (javadoc 中文 GBK 解析炸 → 一律 -x javadoc)
./gradlew :forge:1.20.1:jar :neoforge:1.21.1:jar -x javadoc --no-build-cache
```

## 功能

- **连锁采集** — collect_wood / collect_ore: 连块脉 BFS + 蓄力整脉破坏 + 工具判断 (镐/斧/铲按目标方块) + 换工具不丢旧工具
- **寻路** — 走路全 TLM 原版导航 (maid_useful_task 模式): 上下垂直挖穿 (深度 6) / 危险矿堵护 (6 侧液体堵方块) / 卡方块自救 / 跳过集 TTL
- **哈气互动** — 对女仆/主人哈气 + YSM 动画 + 语音 + 表情气泡 (5s 防刷屏) + 概率挥击
- **任务气泡 API** — MaidChatBubbleApi 5 类 (进度替换式/失败超时 600t 节流) + 任务步骤气泡
- **女仆 GUI** — 独立女仆列表屏 (服务端全维度扫描, 3D 预览) + 属性屏 (16 属性) + 模组主界面 (全景背景)
- **环境感知** — 200t 扫描 18 信号 → 4 被动管线 (铲雪/照明/温度/怪物日志)
- **兼容** — Create 6 生态 (曲柄/动力/压片/搅拌/跑步机/装配) + CBC 火炮装填 (1.20.1) + Numen 假人桥 + YSM 动画注入 (config/animations/ 零代码接入)

## 布局

- `common/` — 平台中性代码 (314 java, `//? if 1.20.1` 条件化)
- `forge/src/` — Forge 专属 (LmaForgeClientEntry + create/cbc compat)
- `neoforge/src/` — NeoForge 专属 (LmaNeoForgeEntry/payload 网络 + Numen 石板桥)
- `versions/<mc>/gradle.properties` — 版本节点唯一真相源 (project.version = 正式版本号)
- `libs/` — 本地 jar 依赖 (不入库)

## 支持节点

- `1.20.1-forge` (MDG legacyforge)
- `1.21.1-neoforge` (MDG, Parchment 2024.11.17 与 TLM 同款)

## 关键约定

- 修改 common 后**必须双节点编译验证** (条件化分支各自独立)
- 版本号: `versions/<mc>/gradle.properties` project.version + 双节点 mods.toml 手写同步; 改后必须清 Stonecutter merged 缓存 (common/versions/*/build) 重打
- neoforge 资源改动后 `:neoforge:1.21.1:copyResourcesToClasses --rerun-tasks` (FML 从 classes 读 mod)
- 专用服务器兼容: 主类字节码不可引用客户端类 (Screen 等) — 客户端注册走独立入口
- 1.21.1 数据包差异: pack_format 48, 结构路径 `structure/` (单数)
- TlmEventAdapter 仅 2 订阅者 (InvariantTest 守护) — 新事件桥走独立类
- 静态缓存管理: maidId key 的静态 map 终结即清 + 实体卸载清理 (EntityCleanupListener)

## Contributors

- [xiaozhaoz1](https://github.com/xiaozhaoz1) — 项目作者
- DeepSeek — AI 编程辅助 (代码生成 / Bug 调试 / 文档)

## License

MIT
