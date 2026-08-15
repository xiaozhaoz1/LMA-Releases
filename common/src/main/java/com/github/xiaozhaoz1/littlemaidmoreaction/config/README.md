# config — 配置层

**作用**: 三段配置 Spec (active/passive/more) + Cloth GUI 绑定。所有用户可调参数的唯一真相。

**判据 (什么进这里)**: 配置定义 (Spec 字段 + 默认值 + 注释); 读配置在业务侧。

**依赖方向**: 被 task/service、vanilla/execute 等读取; 不依赖业务。

**代表**: ActiveTaskConfig (主动任务组) / PassiveTaskConfig (被动组) / MoreActionConfig (总段+saveAll)

**修改注意**: 新增配置必须 ACTIVE_VALUES 等注册 (ConfigConsistencyTest 反射守护); GUI 项与 Spec 双处同步 (ClothSettingsScreen)。
