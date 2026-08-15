# bauble/WildKitsuneMilk — 酒狐奶饰品实现包

**作用**: 酒狐奶桶 / 野生酒狐奶两个奶桶饰品的全部业务实现（继承 `TLM IMaidBauble`）。

**判据**: 具体饰品行为放这里；通用饰品基座（时间戳状态读写）在父包 `bauble/` —— API 与实现分离。

**结构**:
- `MilkKind` — 奶种类枚举 (TAMED / WILD)
- `KitsuneMilkItems` — 两个物品 DeferredRegister
- `TamedMilkBucketItem` / `WildMilkItem` — 可饮物品
- `TamedMilkBauble` / `WildMilkBauble` — 饰品行为（受伤 buff / 濒死无敌）
- `WildKitsuneMilkConfig` — 8 项配置 (`kitsune_milk.toml`)
- `KitsuneMilkInteract` — 右键挤奶转换（驯服/主人三态 + 哈气攻击 + 加好感）
- `KitsuneMilkBaubleRegistry` — `bindMaidBauble` 注册两个 bauble

**依赖方向**: TLM `api.bauble` + LMA `bauble/`(基座) + `task/service`(哈气伤害) + `vanilla/execute`(动画/攻击) + `config`。

**右键交互规则**（主人维度三态）:
| 女仆状态 | 结果 |
|---|---|
| 已驯服 + 自己 | 酒狐奶桶 + 好感 +1 (CD 5min, 仅好感有 CD) |
| 已驯服 + 别人 | 不能挤奶 |
| 未驯服 | 野生奶(副开关开)/奶桶(副开关关) + 哈气动画 + 攻击 |

攻击伤害读哈气管线 `PassiveTaskConfig.HAQI_HIT_DAMAGE`；挤奶本身无 CD。

**修改注意**: 新键（无敌/CD/好感时间戳）必须闭环 —— 无敌键 `onTakeOff` 清，CD/好感为时间戳自过期语义可不清。
