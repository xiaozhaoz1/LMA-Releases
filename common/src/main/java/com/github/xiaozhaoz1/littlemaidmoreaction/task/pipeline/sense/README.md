# task/pipeline/sense — 被动管线层

**作用**: 事件/信号触发的任务 (不挂 TLM 任务栏), 与主动任务平行运行。触发面 + 轻量计时状态机。

> ⚠️ v79.61x 起**纯触发型不再住本包** — structure_sense / festival / rare_biome 迁 `task/passive/impl` (无 pipeline 占位, PassiveDispatcher 驱动); 本包 = **仍走 GMPM 的被动管线 7 类**。

**判据 (什么进这里)**:
- 被动任务 (showInBar=false): onSignal 触发 / 周期扫描触发 (HaqiTrigger) + 计时 tick
- 执行细节 → task/service (HaqiService 先例: 挥击/音效出管线)
- 纯触发型 (零 tick) → `task/passive/impl` 而非本包

**依赖方向**: task/api + task/data + task/service; 被 TaskRegistry 注册 (PASSIVE 规格表)。

**代表**: HaqiPipeline (双通道触发+LOOK 状态机) / HaqiTrigger / TorchLightPipeline / SelfRescuePipeline+SelfRescueTrigger / JiuhuMilkPipeline (jiuhu_milk, 主人低血喂奶) / ExplorerMapPipeline (探险家地图读宝藏坐标)

**驱动桶 (v79.63 — 由 `TaskRegistryManifest.Drive` 声明, 引擎按声明分派)**:
| 管线 | Drive | 语义 |
|---|---|---|
| HaqiPipeline (haqi) | `GMPM_PASSIVE` | FSM + 底层覆盖 (哈气运行中其余被动停 tick) |
| SelfRescuePipeline (self_rescue) | `GMPM_PASSIVE` | 时间关键, 与主动任务并行 |
| ExplorerMapPipeline (explorer_map) | `GMPM_PASSIVE` | 纯信息气泡 — **不受坐下限制** (用户裁定) |
| TorchLightPipeline (torch_light) | `STANDALONE_PASSIVE` | 动作型 (会 moveTo) — **坐下暂停** |
| JiuhuMilkPipeline (jiuhu_milk) | `STANDALONE_PASSIVE` | 动作型 (改主人状态/消耗物品) — **坐下暂停** |

> 开关归属: haqi / jiuhu_milk = `switchScope() = PER_MAID` (缺省关, 子任务界面开); 其余走全局 TaskToggle。

> 已删: SnowShovelPipeline (v79.62 — TLM 原版清雪覆盖) / TempAdaptPipeline (v79.62.5 — 温度改用 TLM `getAtBiomeTemp`) / StructureSense·Festival (迁 passive/impl)。

**修改注意**:
1. 被动键 lma_passive_<type> 与主动 lma_flow_task 是两套平行世界 — submitPassive/cancelPassive 键闭环
2. 哈气互斥 (哈气运行时其他被动停 tick) 是全局规则 — 改互斥先读 GMPM.tickPassiveFor
3. 开关: TaskToggle.isEnabled 未知类型默认开 (黑名单语义)
