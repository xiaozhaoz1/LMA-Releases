# resource — 资源层

**作用**: 资源加载/管理——动态动画资源 (YSM 模型动画合并)。

**代表**: DynamicAnimationResources / LmaAnimationDef (动画元数据 record, 供 storage/execute 读写)

**修改注意**: 资源构建期合并 vs 运行期写源树的边界 (运行期写源树是反模式 — lang key 双文件手改先例)。
