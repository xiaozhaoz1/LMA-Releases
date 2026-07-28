/**
 * IO架构扩展 — 通过 {@code MaidInteractEvent} 桥接女仆交互事件到LMA IO系统。
 *
 * <h3>待实现</h3>
 * <ul>
 *   <li>MaidInteractBridge — 订阅 TLM InteractMaidEvent, 路由到 IReader/IWriter</li>
 *   <li>右键物品交互 → 触发规则引擎条件检查</li>
 *   <li>右键方块交互 → 写入IO状态</li>
 * </ul>
 */
package littlemaidmoreaction.littlemaidmoreaction.io;
