# LMA IO 架构 — NBT持久化 + 容器交互 + 魂符同步

> v61 (2026-07-25) — 根据本次踩坑记录固化模式。改前必读。

## 1. NBT 持久化: PersistentData 写入/读取 完整链路

### 写入链

```
修改库存 → setStackInSlot(slot, stack)    ← 任何改库存的地方必经此路
  └→ super.setStackInSlot(slot, stack)     ← ItemStackHandler 更新内存
  └→ if (serverSide) saveToNBT()           ← 自动写入 PersistentData

saveToNBT():
  root = maid.getPersistentData().getCompound("maid_assembly")
  root.put("Inventory", serializeNBT())     ← ItemStackHandler → CompoundTag
  root.put("Locks", ...)
  maid.getPersistentData().put("maid_assembly", root)  ← 写入 entity ForgeData

游戏保存/魂符收起:
  entity.save() / entity.saveWithoutId()
    → addAdditionalSaveData() / writeAdditionalSaveData()
    → ForgeData("maid_assembly") → 磁盘/物品NBT
```

### 读取链 (注意顺序!)

```java
// ✅ 正确: setSize 先分配空数组, deserializeNBT 再填充
setSize(TOTAL_SLOTS);           // 1. 分配 NonNullList<ItemStack>(12, EMPTY)
deserializeNBT(invTag);         // 2. 内部调 setSize(NBT.Size) + 逐槽填充
// ❌ 错误: deserializeNBT 后调 setSize → 覆盖已恢复数据 → 物品全丢!
```

### 铁律

- **setSize 必须在 deserializeNBT 之前** — setSize 创建新空数组
- **任何改库存必须走 setStackInSlot** — 禁止 item.grow()/shrink() 后不调 setStackInSlot
- **GUI 物品要持久化必须调 setStackInSlot** — Slot.setByPlayer 自动调, quickMove 走 moveItemStackTo 不调
- **serverSide 门禁** — 客户端绝不写入 PersistentData

## 2. 魂符同步: MaidAndItemTransformEvent

魂符使用 `saveWithoutId` 序列化实体, 不保证 ForgeData 包含。通过 TLM 事件强制同步。

### 收起 (ToItem)

```
ItemSmartSlab.storeMaidData(stack, maid):
  maid.saveWithoutId(data)                  ← 序列化实体到物品NBT
  MaidAndItemTransformEvent.ToItem(maid, stack, data)  ← ⚡事件

onMaidToItem(ToItem):
  inv.saveToNBT()                           ← 确保最新状态进 PersistentData
  data.put("lma_assembly", persistentData)  ← 直接写入物品NBT (绕过ForgeData)
```

### 放出 (ToMaid)

```
ItemSmartSlab.spawnFromStore:
  MaidAndItemTransformEvent.ToMaid(maid, stack, maidData)  ← ⚡事件 (maid.load之前!)

onMaidFromItem(ToMaid):
  forge.put("maid_assembly", data.getCompound("lma_assembly"))
  data.put("ForgeData", forge)             ← 注入 ForgeData, 让后续 load() 读
  ↓
  maid.load(maidData)                       ← readAdditionalSaveData 读 ForgeData → 库存恢复
```

### 铁律

- **ToItem**: 写入 `data["lma_assembly"]` (裸NBT, 不放 ForgeData)
- **ToMaid**: 注入 `data["ForgeData"]["maid_assembly"]`, 让 `maid.load()` 的 `readAdditionalSaveData` 自动恢复

## 3. 容器交互: 提取物品

### 提取链

```
消耗物品 (Pipeline/NearbyCollect)
  ↓
1. vanilla Container?  → container.removeItem(slot, count)  ← 优先, 直接操作
2. mod IItemHandler?   → handler.extractItem(slot, count, false)  ← 回退
  ↓
be.setChanged()  ← 必须! 否则客户端不同步
```

### 关键: 区分提取量

```java
// Pipeline consumeFromNearby: 每次配方消耗 1 个
container.removeItem(s, 1);              // ← 正确

// NearbyCollect 收集物品: 整组提取
container.removeItem(s, st.getMaxStackSize());  // ← 通用收集用max

// ❌ 用错: 收集用的 removeItem(MaxStackSize) 当消耗用 → 64个全没
```

### 铁律

- **通用收集** (NearbyCollect): 整组提取, 用 `getMaxStackSize()`
- **配方消耗** (consumeFromNearby): 每次1个, 用 `1`
- **提取后必须 `be.setChanged()`** — 标记方块脏
- **优先 vanilla Container, IItemHandler 回退** — 某些mod容器IItemHandler是副本

## 4. MaidAssemblyInventory 缓存

```java
static Map<UUID, MaidAssemblyInventory> CACHE = new ConcurrentHashMap<>();

public static MaidAssemblyInventory of(EntityMaid maid) {
    var existing = CACHE.get(maid.getUUID());
    if (existing != null && existing.maid == maid) return existing; // 同一实例
    var inv = new MaidAssemblyInventory(maid, true);  // 新实例 → 重新 loadFromNBT
    CACHE.put(maid.getUUID(), inv);
    return inv;
}
```

### 铁律

- **检测 maidead == maid** — 世界重载/魂符放出后 EntityMaid 是新实例
- **不要用 computeIfAbsent** — 不会检测实例变化, 旧缓存永不更新

## 5. 完整保存/加载检查清单

改库存相关代码前, 逐项确认:

- [ ] 写入: 走 `setStackInSlot` → 自动 saveToNBT
- [ ] 读取: `setSize` 在 `deserializeNBT` 之前
- [ ] 魂符: `ToItem` 写 → `ToMaid` 注入 ForgeData
- [ ] 容器: 提取后 `be.setChanged()`
- [ ] 消耗量: 配方用1, 收集用 maxStackSize
- [ ] serverSide: 客户端不写 PersistentData
- [ ] 缓存: `==` 比较 maid 实例, 不只用 UUID
