# bauble — 饰品 API 层

**作用**: 只放女仆饰品通用 API / 基座（继承 `TLM IMaidBauble` 的通用能力、时间戳状态读写），**不写具体饰品业务**。

**判据**: 具体某个饰品的行为逻辑放子包（如 `bauble/WildKitsuneMilk/`），本层只提供可复用基座 —— API 与实现分离。

**依赖方向**: 只依赖 TLM (`api.bauble.IMaidBauble`) + LMA `task/data`；被具体饰品实现依赖，不反向。

**代表**: `BaubleApi`（服务端时间戳状态：无敌期/CD 的 PersistentData 闭环读写）。

**注册入口**: `LittleMaidMoreActionExtension.bindMaidBauble(BaubleManager)` → 各实现包自己的 Registry。

**修改注意**:
1. 本层新增方法必须通用（多饰品会复用），单饰品专用逻辑放实现包。
2. PersistentData 键必须闭环（set → remove），判定用 `stored > now || stored == 0` 防溢出。
