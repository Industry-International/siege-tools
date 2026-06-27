---
name: 1.21.1-KubeJS7 踩坑记录
description: 在 1.21.1-NeoForge + KubeJS 7 环境下开发时遇到的 API 不兼容、方法缺失、参数变更等踩坑记录。每次遇到新坑，先查此文档；若已有记录但花样不同，追加新条目；若无记录则创建新条目。
---

# 1.21.1-KubeJS7 踩坑记录

> 本文件记录在 1.21.1-NeoForge + KubeJS 7 (Rhino) 环境中，KubeJS 桥接未暴露或 Minecraft 版本变更导致的方法缺失/参数变化。
>
> 所有坑均经过**日志报错验证**，每条记录包含：报错信息、原因、修复方式。

---

## 条目格式

每条踩坑记录按以下格式：

```
## [#编号] 坑名

**错误信息：** `ActualErrorType: ...`

**原因：** 一句话说明为什么不能用

**修复：** 从旧写法 → 新写法

**参考文件：** `文件名:行号`
```

---

## [#1] entity.getPosition() 不存在

**错误信息：** `InternalError: Can't find method net.minecraft.world.entity.Entity.getPosition().`

**原因：** 在 1.21.1 中，`Entity` 类的 `getPosition()` 方法被移除或未被 KubeJS 7 的 Rhino 桥接暴露。

**修复：**

```javascript
// ❌ 错误
let pos = entity.getPosition()
let x = pos.x, y = pos.y, z = pos.z

// ✅ 正确
let x = entity.getX()
let y = entity.getY()
let z = entity.getZ()
```

**参考文件：** `ammo_replenish/main.js:145`

---

## [#2] entity.getStringUUID() 不存在

**错误信息：** `TypeError: Cannot find function getStringUUID in object <EntityType>`

**原因：** `getStringUUID()` 虽然是 Minecraft 标准 Java 方法，但未被 KubeJS 7 Rhino 桥接到自定义实体（如 SBW 的 `T90aEntity`）上。

**修复：**

```javascript
// ❌ 错误
let uuid = entity.getStringUUID()

// ✅ 正确
let uuid = entity.uuid.toString()
```

**参考文件：** `ammo_replenish/main.js:172`

---

## [#3] CompoundTag.getList(key) 需要第二个参数

**错误信息：** `InternalError: Can't find method net.minecraft.nbt.CompoundTag.getList(string).`

**原因：** 1.21.1 的 `CompoundTag.getList()` 方法签名变更为 `getList(String key, int elementType)`，强制要求指定列表元素的 NBT 类型 ID。只传 key 的旧重载已被移除。

**NBT 类型 ID 速查：**

| 类型 | ID | Java 常量 |
|------|----|-----------|
| TAG_BYTE | 1 | `net.minecraft.nbt.Tag.TAG_BYTE` |
| TAG_SHORT | 2 | `net.minecraft.nbt.Tag.TAG_SHORT` |
| TAG_INT | 3 | `net.minecraft.nbt.Tag.TAG_INT` |
| TAG_LONG | 4 | `net.minecraft.nbt.Tag.TAG_LONG` |
| TAG_FLOAT | 5 | `net.minecraft.nbt.Tag.TAG_FLOAT` |
| TAG_DOUBLE | 6 | `net.minecraft.nbt.Tag.TAG_DOUBLE` |
| TAG_STRING | 8 | `net.minecraft.nbt.Tag.TAG_STRING` |
| TAG_LIST | 9 | `net.minecraft.nbt.Tag.TAG_LIST` |
| **TAG_COMPOUND** | **10** | `net.minecraft.nbt.Tag.TAG_COMPOUND` |

**修复：**

```javascript
// ❌ 错误（单参数）
let items = inventory.getList('Items')

// ✅ 正确（带类型 ID）
let items = inventory.getList('Items', 10)   // 10 = TAG_COMPOUND
```

**参考文件：** `ammo_replenish/main.js:302`

---

## [#4] level.getEntitiesOfClass(Class, AABB) 静默失败

**错误信息：** 无报错，但返回空集合（或无法正确调用 Java 泛型方法）

**原因：** KubeJS 7 (Rhino) 中，`Java.loadClass()` 返回的包装类无法直接作为 Java 泛型 `Class<T>` 参数传递到 `level.getEntitiesOfClass()` 中。调用静默失败/返回空。

**修复：**

```javascript
// ❌ 错误
var Entity = Java.loadClass('net.minecraft.world.entity.Entity')
var aabb = new AABB(...)
var entities = level.getEntitiesOfClass(Entity, aabb)  // 泛型参数解析失败

// ✅ 正确：遍历全部 + 手动过滤
var entities = level.getEntities()
var iter = entities.iterator()
while (iter.hasNext()) {
  let entity = iter.next()
  let ex = entity.getX(), ey = entity.getY(), ez = entity.getZ()
  let dx = Math.abs(ex - centerX), dy = Math.abs(ey - centerY), dz = Math.abs(ez - centerZ)
  if (dx <= range && dy <= range && dz <= range) {
    // 处理范围内的 entity
  }
}
```

**参考文件：** `ammo_replenish/main.js`（最初版本，已移除 AABB 方案）

---

## [#5] 跨 session 持久化 gameTime 不连续导致负数计时

**错误信息：** 无报错，但日志显示 `已在范围内 -69.5s / 需 3s`（负数持续不归零）

**原因：** `VehicleTimers` 存储在方块 `persistentData` 中持久化。服务器重启后，`level.getTime()` 返回的 gameTime 值与旧会话不连续（可能变小），导致 `gameTime - storedTimerValue` 为负数。负数的 elapsed 永远无法满足 `>= enterDelayTicks`，补给永不触发。

**修复：** 检测到 `elapsed < 0` 时，将 timer 重置为当前 gameTime：

```javascript
// ❌ 错误：直接使用存储的旧 gameTime
let elapsed = gameTime - timers[uuid]
// elapsed 可能为负数，导致永远达不到阈值

// ✅ 正确：检测负数并重置
let elapsed = gameTime - timers[uuid]
if (elapsed < 0) {
  timers[uuid] = gameTime  // 重置为当前 gameTime
  elapsed = 0
}
```

**参考文件：** `ammo_replenish/main.js:192-200`

---

## [#6] entity.nbt = nbt 重置整个实体状态，玩家乘坐时断连

**错误现象：** 补给站对空载具有效，但玩家坐在车上时补给不生效（弹药数量不增加）

**原因：** `entity.nbt = nbt` 在 KubeJS 7 中会调用 `Entity.readAdditionalSaveData()` 方法，从 NBT 完整反序列化整个实体。这会：
1. 重置乘客（玩家）的连接状态 → 玩家可能被"弹下车"
2. 重置车辆物理运行时状态（运动、旋转、武器充能等）
3. 不同步到客户端

**修复：** 使用 `/data merge entity` 命令替代直接 NBT 赋值，该命令仅合并指定字段，不影响其他运行时状态：

```javascript
// ❌ 错误：整体替换 NBT
entity.nbt = nbt
// readAdditionalSaveData() 被调用，重置整个实体

// ✅ 正确：仅合并 Inventory 字段
inventory.put('Items', items)
let inventorySnbt = inventory.toString()  // CompoundTag.toString() 输出 SNBT 格式
let server = level.getServer()
let cmd = 'data merge entity ' + uuid + ' {Inventory:' + inventorySnbt + '}'
server.runCommandSilent(cmd)
```

**注意：** `CompoundTag.toString()` 输出的是 SNBT（Stringified NBT）格式，与 `/data merge` 命令兼容。

**参考文件：** `ammo_replenish/main.js:419-428`

---

## 使用规则

1. **编写新代码前**：快速浏览此文档，看当前要调用的 API 是否在踩坑列表中
2. **遇到新报错时**：
   - 先查此文档是否已有相同报错
   - 若有但修复方法不同（同一方法不同场景），追加新条目
   - 若无，新增条目
3. **误报检查**：如果某方法在 ProbeJS `.d.ts` 中存在但运行时报错，多半是 Rhino 桥接不完整，应记录到此文档
