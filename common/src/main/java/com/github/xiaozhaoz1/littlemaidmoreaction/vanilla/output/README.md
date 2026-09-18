# vanilla/output — io 写原语层 (11 类)

**作用**: 与世界的单次**写入/交换** — 容器存取/掉落/伤害/状态写/移动/声音。通用、无业务语义。
**判据 (两轴表)**: 无状态 + 无业务语义 + 写世界 → 这里; 多步业务动作 → `task/service` (`FurnaceOutput`/`JukeboxOutput` 已迁走 = 先例)。
**依赖方向**: 只依赖 MC (+ `api.VanillaConstants`); 被 service/execute/pipeline 依赖。
**计数口径**: 本包 **11 类** (逐类 `find` 实测, 2026-09-13) —— **改本包必须同步此表**。

## 一、逐类明细 (行数 / 写什么 / 典型调用方)
| 类 | 子包 | 行数 | 写什么 | 调用方 |
|---|---|---|---|---|
`ContainerOutput` | `container/` | 177 | **容器六方向能力统一入口** (存/取/换手/丢弃策略) | 各 service |
`ContainerExtractor` | `container/` | 124 | 附近容器**提取** (V3 自 input 拆出的写半边) | 各 service |
`MaidStateWriter` | `maid/` | 380 | **女仆状态写入门面** (最大写侧类) | 各层 |
`ThrottleUtil` | `maid/` | 44 | **节流写** (写冷却时间戳; 判定委托 `ThrottleMath`) | service · pipeline |
`MaidSwing` | `maid/` | 23 | 挥臂动作 (视觉/交互前置) | 采集/交互类 |
`CombatOutput` | `combat/` | 142 | 伤害/效果施加 | 防御塔 · 哈气 |
`ItemSpawner` | `item/` | 28 | 物品**落地** (掉落物) | service |
`HandSwap` | `item/` | 62 | **换手复合原语** (主/副手交换 + 回滚语义) | service |
`BrainHelper` | `movement/` | 24 | 导航/移动**写**原语 (设 WALK_TARGET 等) | 工作站门 · 移动类任务 |
`SoundOutput` | 顶层 | 52 | 声音原语 (`playAt`) | 敲钟 · 哈气 |
`ProgressNotifier` | 顶层 | 11 | 进度**气泡/通知** | pipeline · service |
| **空目录 (用户裁定保留, 无 java)** | `altar/` `block/` `effect/` `entity/` `visual/` `world/` | — | 历史分包残留 | 新增原语按两轴判据落位即可 |

## 二、连接链 (与读侧对称)
```
task/pipeline (相位机)
  → output/* 一次写: 存物(ContainerOutput) · 掉落(ItemSpawner) · 伤害(CombatOutput)
                    · 状态(MaidStateWriter) · 移动(BrainHelper) · 音效/气泡(SoundOutput/ProgressNotifier)
判定/节拍/业务语义留在调用方; 本层只保证"这一次写做对且可回滚"。
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **业务类别再住进来** | 层名与内容漂移是已犯过的病 (`FurnaceOutput`/`JukeboxOutput` 已迁 `task/service`) ⇒ 有业务语义的写一律去 service。|
**B** | **物品写入返回值不能丢** | 丢物品族 (`错题 #162`): 放回/背包/落地**三链都要处理返回值** (部分放入要留余量)。⚠ **"退还类" insert 同样要接余量** (v79.68.1, 错题 #354 族审计): 把物品**退还给刚被 extract 的同一槽/背包**时, 返回值也**不能丢** — 正常必有空间 (不可复现), 但目标 handler 有 insert 筛选语义 (mod 容器) 会拒收 ⇒ 余量交 `HandSwap.stashOrDrop` (背包 → 落地) / `ItemSpawner.spawnForPickup` ✓ |
**C** | **写原语的"回滚"语义 + 覆盖式写手必须带旧物去向** | 复合写 (`HandSwap`) 失败时状态要还原, 否则物品消失/复制 (复合 io 原语合法的前提是**可回滚**)。⚠ **凡"把某物放到主/副手"都必须处理旧物**: 要么 save/restore, 要么用 `HandSwap.extractSlotStashOld` / `stashOrDrop` 保全链 (**整槽提取 + 旧物三链: 槽位 → 背包 → 落地**) — **直接 `setItemInHand` 覆盖 = 旧物永久消失** ✗ (错题 #162 丢物品族: ChainHarvestExecute/BlockUpCoordinator 已收敛; **dam_fill 换桶**曾漏网 ⇒ lessons #354) |
**D** | **节流判定与写分离** | `ThrottleUtil` 只管"记时间戳"; 是否该调用由上层按节拍决定 (`ThrottleMath` 纯判定在 input/maid)。|
**E** | **越层红线** | 本层不得 import task/compat/storage 等上层 (ArchGuard 零容忍)。|
