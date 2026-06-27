---
name: SBW卓越前线开发
description: SuperB Warfare / 卓越前线模组的 KubeJS 开发指南。涵盖载具系统架构（8状态状态机）、载具配置与部署、弹药补给站、NBT 数据结构、常用工具函数与指令。适用于所有涉及 sbw_vehicle 模块和 superbwarfare 物品/实体的开发任务。
---

# SBW 卓越前线 — KubeJS 开发指南

SuperB Warfare（卓越前线，简称 SBW）是一个国产 Minecraft 模组，添加了现代军事载具（坦克、装甲车等）及其相关武器系统。本项目的 KubeJS 脚本通过 `server_scripts/sbw_vehicle/` 模块对其进行深度整合。

---

## 1. 模块架构总览

```
sbw_vehicle/
├── config.js                    载具配置（队伍、坐标、deployNBT 模板）
├── main.js                      模块入口（全局常量 + 死亡事件监听）
├── command.js                   指令注册（8 个命令）
├── replenish.js                 自动补员系统（状态机驱动）
│
├── ammo_replenish/              弹药补给站子系统
│   ├── a_config.js              默认配置 & 弹药类型映射
│   └── main.js                  方块核心逻辑（AABB扫描、补给）
│
└── tools/
    ├── a_java_refs.js           Java 类引用（优先加载）
    ├── state_machine.js         状态机核心（8状态枚举 + 转移规则）
    ├── deploy.js                载具部署
    ├── entity.js                实体查找 / 区块检测 / 批量清理
    ├── persist.js               持久化数据 & 系统开关
    ├── nbt.js                   JSON → NBT 转换工具
    ├── log.js                   日志工具
    ├── misc.js                  杂项工具（ID提取 / 配置查找 / 清除 / 重置）
    └── status.js                状态查询 & ActionBar
```

---

## 2. 模组物品/实体 ID 规范

所有 SBW 模组物品和实体均以 `superbwarfare:` 为命名空间：

| 类型 | 格式 | 示例 |
|------|------|------|
| 载具实体 | `superbwarfare:<载具名>` | `superbwarfare:t_90a` |
| 弹药物品 | `superbwarfare:<弹药类型>` | `superbwarfare:large_shell_ap` |
| 其他物品 | `superbwarfare:<物品名>` | `superbwarfare:rifle_ammo` |

### 2.1 弹药类型对照表

| 弹药短名 | 完整 ID | 说明 |
|---------|---------|------|
| `large_shell_ap` | `superbwarfare:large_shell_ap` | 大口径 AP 弹（穿甲弹） |
| `large_shell_he` | `superbwarfare:large_shell_he` | 大口径 HE 弹（高爆弹） |
| `large_shell_gs` | `superbwarfare:large_shell_gs` | 大口径葡萄弹 |
| `small_shell_ap` | `superbwarfare:small_shell_ap` | 小口径 AP 弹 |
| `small_shell_he` | `superbwarfare:small_shell_he` | 小口径 HE 弹 |
| `small_shell_gs` | `superbwarfare:small_shell_gs` | 小口径葡萄弹 |
| `small_shell_aa` | `superbwarfare:small_shell_aa` | 小口径防空弹 |
| `rifle_ammo` | `superbwarfare:rifle_ammo` | 步枪弹（机枪用） |
| `heavy_ammo` | `superbwarfare:heavy_ammo` | 重弹 |
| `missile` | `superbwarfare:missile` | 导弹 |
| `rocket` | `superbwarfare:rocket` | 火箭弹 |
| `small_rocket` | `superbwarfare:small_rocket` | 小型火箭弹 |
| `medium_anti_ground_missile` | `superbwarfare:medium_anti_ground_missile` | 中型对地导弹 |
| `large_anti_ground_missile` | `superbwarfare:large_anti_ground_missile` | 大型对地导弹 |
| `medium_anti_air_missile` | `superbwarfare:medium_anti_air_missile` | 中型防空导弹 |
| `mortar_shell` | `superbwarfare:mortar_shell` | 迫击炮弹 |
| `medium_aerial_bomb` | `superbwarfare:medium_aerial_bomb` | 中型航弹 |
| `small_aerial_bomb` | `superbwarfare:small_aerial_bomb` | 小型航弹 |

### 2.2 载具标识方式

- 通过实体的 NBT `id` 字段判断是否为 SBW 载具（以 `superbwarfare:` 开头）
- `isSBWVehicle(entity)` 工具函数封装了此判断逻辑
- 每辆载具通过 KubeJS 实体标签（tag）追踪，前缀为 `sbw_vehicle_` + 载具 ID

---

## 3. 载具 NBT 数据结构

### 3.1 核心属性

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `Energy` | int | 0 | 载具能量/电力，影响武器可用性，0=没电 |
| `Health` | float | 500.0 | 载具总生命值，归零则摧毁 |
| `Invulnerable` | byte/0\|1 | 0 | 无敌模式，1=无法被伤害 |
| `IsWreck` | byte/0\|1 | 0 | 是否残骸状态，1=已报废形态 |
| `Power` | float | 0.0 | 动力输出，影响移动速度 |
| `Fire` | short | -1 | 着火剩余时间（-1=不烧，>=1=燃烧中） |
| `FallDistance` | float | 0.0 | 累积坠落距离 |
| `GearUp` | byte/0\|1 | 0 | 起落架收起 |
| `GearRot` | float | 0.0 | 起落架/齿轮旋转角度 |

### 3.2 部件健康度

每个部件都有 `Health`（健康度 float）和 `Damaged`（是否损坏 byte/0|1）两个字段。当 `Health` 归零时 `Damaged` 自动变为 1。

| 字段前缀 | 说明 |
|---------|------|
| `LeftWheel` | 左轮（Health: 100.0, Damaged: 0） |
| `RightWheel` | 右轮（Health: 100.0, Damaged: 0） |
| `MainEngine` | 主引擎（Health: 150.0, Damaged: 0） |
| `SubEngine` | 副引擎（Health: 150.0, Damaged: 0） |
| `Turret` | 炮塔（Health: 100.0, Damaged: 0） |

炮塔额外有两个字段：
- `TurretBurned`（byte/0|1）：炮塔是否烧毁
- `TurretBurnTimer`（int）：炮塔燃烧计时（tick）

### 3.3 武器系统

| 字段 | 类型 | 说明 |
|------|------|------|
| `DecoyReady` | byte/0\|1 | 诱饵弹是否装填就绪 |
| `ChargeProgress` | float | 特殊武器充能进度（0.0~1.0） |
| `WeaponState` | compound | 各武器的详细状态（嵌套结构） |

**WeaponState 嵌套结构**：

```
WeaponState: {
  Cannon: {                    // 主炮
    components: {
      "minecraft:custom_data": { GunData: { Ammo: <int> } }
    }
  },
  MachineGun: {                // 同轴机枪
    components: {
      "minecraft:custom_data": { GunData: { Ammo: <int> } }
    }
  },
  PassengerMachineGun: {       // 乘客机枪
    components: {
      "minecraft:custom_data": { GunData: { Ammo: <int> } }
    }
  }
}
```

### 3.4 载具库存（Inventory）

```
Inventory: {
  Items: [
    { Slot: 0,  count: 63, id: "superbwarfare:large_shell_ap" },
    { Slot: 1,  count: 64, id: "superbwarfare:large_shell_he" },
    // ... 更多弹药
  ]
}
```

- `Slot` 编号范围：`0~53`（共 54 格）

### 3.5 Motion 与 Rotation

```
Motion: [0.0, 0.0, 0.0]       // 速度向量 [x,y,z]（部署时保持静止）
Rotation: [yaw, pitch]         // 由部署代码自动设置
```

---

## 4. 载具部署系统

### 4.1 8 状态状态机

定义在 `tools/state_machine.js`，通过 `VEHICLE_STATE` 常量和 `transitionState()` 函数驱动。

| 状态常量 | 值 | 含义 |
|---------|-----|------|
| `VEHICLE_STATE.UNINITIALIZED` | `'uninitialized'` | 未初始化（首次创建或系统重置后） |
| `VEHICLE_STATE.IDLE` | `'idle'` | 空闲（等待部署条件满足） |
| `VEHICLE_STATE.WAITING_CHUNK` | `'waiting_chunk'` | 等待区块加载 |
| `VEHICLE_STATE.CHUNK_LOADED` | `'chunk_loaded'` | 区块已加载（可部署） |
| `VEHICLE_STATE.DEPLOYED` | `'deployed'` | 载具已部署（正常存活） |
| `VEHICLE_STATE.OVER_CAPACITY` | `'over_capacity'` | 载具超量（超出 maxCount） |
| `VEHICLE_STATE.UNDER_CAPACITY` | `'under_capacity'` | 载具不足（存活数 < maxCount） |
| `VEHICLE_STATE.TIMING` | `'timing'` | 计时中（重生倒计时） |

### 4.2 状态转移规则

| 当前状态 | 允许的下一个状态 | 触发条件 |
|----------|-----------------|----------|
| `UNINITIALIZED` | `IDLE` | 初始化完成 |
| `IDLE` | `WAITING_CHUNK` / `CHUNK_LOADED` | 区块未加载/已加载 |
| `WAITING_CHUNK` | `CHUNK_LOADED` / `DEPLOYED` / `IDLE` | 区块加载/直接部署/复位 |
| `CHUNK_LOADED` | `DEPLOYED` / `OVER_CAPACITY` / `IDLE` | 执行部署/超量/跳过 |
| `DEPLOYED` | `TIMING` / `OVER_CAPACITY` / `IDLE` | 被摧毁/超量/手动清除 |
| `OVER_CAPACITY` | `TIMING` / `DEPLOYED` / `CHUNK_LOADED` / `IDLE` | 被摧毁/恢复/区块就绪/手动 |
| `UNDER_CAPACITY` | `IDLE` / `WAITING_CHUNK` / `TIMING` | 重新部署/区块未加载/补员 |
| `TIMING` | `OVER_CAPACITY` / `CHUNK_LOADED` / `WAITING_CHUNK` / `IDLE` | 计时完成 |
| **任意状态** | `UNINITIALIZED` | 系统重置（强制） |

### 4.3 状态辅助判断函数

```javascript
isDeployed(state)          // 是否已部署（state === 'deployed'）
isReplenishing(state)      // 是否在补员流程中（timing/waiting_chunk/under_capacity）
isReadyToDeploy(state)     // 是否可部署（state === 'chunk_loaded'）
isActive(state)            // 是否在活跃状态
needsReplenish(state)      // 是否需要补员介入
isWarning(state)           // 是否异常状态（over_capacity）
```

### 4.4 状态转移执行

```javascript
// 带校验的状态转移
transitionState(server, vehicleId, VEHICLE_STATE.TIMING, { remainingTicks: 1200 })

// 强制设置状态（跳过校验，仅用于系统级重置）
forceSetState(server, vehicleId, VEHICLE_STATE.IDLE)

// 初始化状态条目
initVehicleState(server, teamName, vehicleCfg)
```

---

## 5. 配置指南

### 5.1 配置结构（config.js）

```javascript
const SBW_VEHICLE_CONFIG = {
  persistKey: 'sbw_vehicle',       // 持久化数据存储键名
  tagPrefix: 'sbw_vehicle_',       // 实体标签前缀
  checkInterval: 20,               // 补员检测频率（tick，20=每秒1次）

  teams: {
    attack: {                       // 队伍名（小写）
      vehicles: [
        {
          id: 'attack_tank_1',              // 唯一标识符
          vehicleType: 'superbwarfare:t_90a', // 载具实体类型
          pos: [x, y, z, yaw, pitch],        // 生成坐标 + 朝向
          respawnDelay: 600,                  // 重生延迟（tick，600=30秒）
          maxCount: 1,                        // 最大同时存活数（0=不限制）
          deployNBT: { /* 部署模板，见第3节 */ }
        }
      ]
    }
  }
}
```

### 5.2 deployNBT 模板编写要点

- **不写或设为 `null`** → 白板生成（无能量、无弹药）
- 每辆载具的 `deployNBT` **完全独立**
- NBT 类型特殊处理：需要明确指定类型时使用 `{ __nbt_type: "byte", value: 1 }`
- 支持的类型：`byte`, `short`, `long`, `float`, `double`

### 5.3 maxCount 最大数量控制

| 功能 | 说明 |
|------|------|
| 部署检查 | 部署前扫描世界存活数，已达上限则跳过 |
| 自动检测 | `DEPLOYED` 状态自动检查，超量标记 `OVER_CAPACITY` |
| 跨队隔离 | 不同队伍的同名 ID 通过队伍前缀隔离 |
| 不设置或 `0` | 表示不限制数量 |

---

## 6. 指令系统

所有指令需要 OP 2 权限：

| 指令 | 说明 |
|------|------|
| `/sbw_vehicle start` | 激活系统 + 初始化状态 + 启动补员循环 |
| `/sbw_vehicle stop` | 停用系统 + 停止补员循环 + 清除所有载具 |
| `/sbw_vehicle deploy [<队伍>]` | 部署指定/所有队伍的载具 |
| `/sbw_vehicle redeploy` | 强制重新部署（清旧+重部署） |
| `/sbw_vehicle reset` | 重置所有载具状态 |
| `/sbw_vehicle clear [<队伍>]` | 清除载具实体+重置状态 |
| `/sbw_vehicle status` | 查看系统状态 + 各载具状态 |
| `/sbw_vehicle timelist` | 查看所有补员倒计时列表 |

---

## 7. 弹药补给站

### 7.1 方块信息

- **方块ID**: `kubejs:ammo_crate`
- **核心功能**: AABB 扫描周围载具 → 检查 Inventory 弹药 → 补到配置最大值 → 进入冷却
- **默认方块配置**: 在 `ammo_replenish/a_config.js` 中定义

### 7.2 补给站配置字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `scanRange` | number | 12 | 扫描半径（方块） |
| `cooldown` | number | 5 | 冷却时间（秒） |
| `slots` | Object | 见默认配置 | 弹药类型 → 最大储量 |

### 7.3 关键 API

```javascript
// 判断实体是否为 SBW 载具
isSBWVehicle(entity)

// 补给单辆载具弹药
replenishVehicle(entity, slots)

// 读取方块配置
readBlockConfig(block)

// 执行一次扫描补给
executeStationReplenish(block, level, ignoreCooldown)
```

---

## 8. 核心 Java 类引用

以下是 `tools/a_java_refs.js` 中声明的全局 Java 类引用，所有子模块可直接使用：

```javascript
var $UUID               = Java.loadClass('java.util.UUID')
var $ListTag            = Java.loadClass('net.minecraft.nbt.ListTag')
var $FloatTag           = Java.loadClass('net.minecraft.nbt.FloatTag')
var $StringTag          = Java.loadClass('net.minecraft.nbt.StringTag')
var $DoubleTag          = Java.loadClass('net.minecraft.nbt.DoubleTag')
var $LongTag            = Java.loadClass('net.minecraft.nbt.LongTag')
var $ShortTag           = Java.loadClass('net.minecraft.nbt.ShortTag')
var $HashMap            = Java.loadClass('java.util.HashMap')
var $HashSet            = Java.loadClass('java.util.HashSet')
var $Component          = Java.loadClass('net.minecraft.network.chat.Component')
var $ResourceLocation   = Java.loadClass('net.minecraft.resources.ResourceLocation')
var $ArrayList          = Java.loadClass('java.util.ArrayList')
var $CompoundTag        = Java.loadClass('net.minecraft.nbt.CompoundTag')  // 常用但未在此声明
var $AABB               = Java.loadClass('net.minecraft.world.phys.AABB')  // ammo_replenish 中使用
```

---

## 9. 常用工具函数速查

### 9.1 日志输出

```javascript
sbwLog('消息')     // console.log 加前缀 [SBW载具]
sbwWarn('警告')    // console.warn
sbwError('错误')   // console.error
```

### 9.2 持久化数据

```javascript
getStore(server)         // 读取全部持久化数据 { active, vehicles: {...} }
saveStore(server, data)  // 保存持久化数据
isSystemActive(server)   // 检查系统是否激活
setSystemActive(server, true/false)  // 设置系统激活状态
```

### 9.3 实体操作

```javascript
findEntityByUUID(server, uuidStr)    // 通过 UUID 查找实体
findEntityByTag(server, tag)         // 通过标签查找实体
findVehicleEntity(server, state, tag) // 查找载具实体（UUID优先，再按标签）
countAliveByTag(server, tag)         // 统计带标签的存活实体数量
discardAllByTagPrefix(server)        // 批量清除所有带标签前缀的实体
hasNearbyPlayer(server, x, z, dim, range)  // 检查附近是否有玩家
isChunkLoaded(server, x, z, dimension)     // 检查区块是否加载
```

### 9.4 配置查找

```javascript
findVehicleConfig(vehicleId)   // 根据 ID 查找载具配置
findVehicleTeam(vehicleId)     // 根据 ID 查找所属队伍
extractVehicleIdFromEntity(entity)  // 从实体标签提取载具 ID
```

### 9.5 NBT 工具

```javascript
toNBT(obj)           // JSON 对象 → NBT Tag
mergeDeployNBT(target, source)  // 合并 deployNBT 到目标 CompoundTag
```

### 9.6 部署接口

```javascript
deployVehicle(server, teamName, vehicleCfg)       // 部署单辆载具
deployTeamVehicles(server, teamName)               // 部署整个队伍
deployAllVehicles(server)                          // 部署所有队伍
spawnVehicleEntity(server, vehicleCfg)             // 底层：召唤实体
```

---

## 10. 补员循环

### 10.1 生命周期钩子

```javascript
// 启动补员循环（通常在系统激活时调用）
startReplenishLoop(server)

// 停止补员循环
stopReplenishLoop()

// 强制初始化所有载具状态
initAllVehicleStates(server)
```

### 10.2 频率换算

```javascript
// config.js 中的 checkInterval = 20 → 每 20 tick 检测一次
// 即每秒 1 次全量检测
getCheckTickInterval()  // 返回实际 tick 间隔
```

### 10.3 重启保底机制

补员循环启动时自动检测 `TIMING` 状态中 `remainingTicks > 0` 的残留条目，将其置为 `0`，下一轮循环立即处理部署。

### 10.4 定期持久化

每 `SAVE_INTERVAL = 5` 次补员循环强制全量保存一次，防止崩溃丢数据。`remainingTicks` 在每次递减后直接落盘。

---

## 11. 状态图标映射（ActionBar / Status）

| 图标 | 状态 | 颜色 |
|------|------|------|
| `✓` | DEPLOYED（存活） | 绿色/黄色/红色（按血量） |
| `⟳` | TIMING（补员倒计时） | 黄色 |
| `◐` | WAITING_CHUNK（等待区块） | 灰色 |
| `◑` | CHUNK_LOADED（区块就绪） | 淡蓝 |
| `⚠` | OVER_CAPACITY（载具超量） | 红色 |
| `⬇` | UNDER_CAPACITY（载具不足） | 黄色 |
| `?` | UNINITIALIZED（未初始化） | 灰色 |
| `○` | IDLE（空闲） | 灰色 |

---

## 12. 反编译模组获取原始载具数据

当需要核对某辆载具的确切武器配置（弹药类型 `AmmoType`、武器名 `Name`、伤害参数等）时，可直接从模组 JAR 中提取原始 JSON。

### 12.1 JAR 路径

模组 JAR 文件位于游戏版本的 `mods/` 目录下。文件名含版本号，可用通配符：

```
mods/
├── *superbwarfare*.jar        # SBW 卓越前线本体
└── MCSP*.jar                   # MCSP 附属模组
```

### 12.2 提取命令

使用 JDK 自带的 `jar` 命令（无需解压工具）：

```bash
# 先 cd 到游戏版本目录（注意路径含空格要加引号）
cd "d:/path/to/.minecraft/versions/<版本名称>/"

# 提取 SBW 本体载具数据（data 目录下的配置，不是 assets 的模型）
jar xf "mods/*superbwarfare*.jar" "data/superbwarfare/sbw/vehicles/"

# 提取 MCSP 附属载具数据
jar xf "mods/MCSP*.jar" "data/mcsp/sbw/vehicles/"

# 可选：提取装配配方（也含弹药配方参考）
jar xf "mods/*superbwarfare*.jar" "data/superbwarfare/recipe/"
jar xf "mods/MCSP*.jar" "data/mcsp/recipes/"
```

### 12.3 提取后的文件路径

| 数据 | 路径 |
|------|------|
| SBW 载具配置 | `data/superbwarfare/sbw/vehicles/<载具名>.json` |
| SBW 示例文件 | `data/superbwarfare/sbw/vehicles/vehicles.example.jsonc` |
| SBW 配方数据 | `data/superbwarfare/recipe/<配方名>.json` |
| MCSP 载具配置 | `data/mcsp/sbw/vehicles/<载具名>.json` |
| MCSP 弹药配方 | `data/mcsp/recipes/ammunition/<弹药名>.json` |

### 12.4 关键字段速查

提取后，在载具 JSON 的 `Weapons` 对象下查看每把武器的 `AmmoType` 字段：

```json
"Weapons": {
  "Cannon": {
    "AmmoType": [                                  // ← 弹药类型（重点）
      "superbwarfare:large_shell_ap",               //   string = 直接可用弹药
      {                                             //   object = 弹药 + 参数覆盖
        "Ammo": "superbwarfare:large_shell_he",
        "Override": { "Damage": 250, ... }
      }
    ],
    "Name": "weapon.superbwarfare.cannon_ap",       // i18n key
    "Projectile": "superbwarfare:cannon_shell",     // 抛射物
    "Damage": 700, "ExplosionRadius": 4             // 伤害参数
  },
  "MachineGun": {
    "AmmoType": "@RifleAmmo",                      // @前缀 = 引用预定义类型
  },
  "PassengerMachineGun": {
    "AmmoType": "@HeavyAmmo"                        // → 对应 heavy_ammo
  }
}
```

`AmmoType` 字段取值规则：

| 格式 | 含义 | 示例 |
|------|------|------|
| `"物品ID"` | 指定物品为弹药 | `"superbwarfare:medium_anti_ground_missile"` |
| `[数组]` | 多弹药可选，含 Override | `["superbwarfare:small_shell_ap", {...}]` |
| `"@RifleAmmo"` | 引用预定义 → `rifle_ammo` | 展开为 `superbwarfare:rifle_ammo` |
| `"@HeavyAmmo"` | 引用预定义 → `heavy_ammo` | 展开为 `superbwarfare:heavy_ammo` |
| `"FE"` | 能量武器，不耗弹药物品 | 无需补充弹药 |

### 12.5 快速核对清单

提取 JSON 后，按以下步骤核对弹药映射：

1. 找到 `Weapons` 对象，列出所有武器名（如 `Cannon`、`MachineGun`、`Missile` 等）
2. 对每个武器读取 `AmmoType`，收集所有用到的弹药 ID
3. 将弹药 ID 与 `a_config.js` 中 `AMMO_ID_MAP` 的 value 比对
4. 将载具实体注册名（`superbwarfare:<载具名>`）与 `vehicle_ammo_config/` 中的 key 比对
5. 更新 `VEHICLE_AMMO_MAP` 条目，确保包含所有 `AmmoType` 列出的弹药

### 12.6 清理提取文件

```bash
# 用完即清理，避免污染工作区
rm -rf data/superbwarfare/ data/mcsp/
```

---

## 13. 开发注意事项

1. **NBT 读写**：所有方块 NBT 读写后必须调用 `block.entity.setChanged()`，否则重启后数据丢失
2. **实体清理**：使用 `entity.discard()` 替代 `entity.kill()`，不留掉落物
3. **persistentData**：`block.entity.persistentData` 有效，`block.persistentData`（LevelBlock）不存在
4. **不要使用** `block.getEntityData()` / `setEntityData()`
5. **模块加载顺序**：`a_java_refs.js` 因 `a_` 前缀优先加载，tools 中的函数定义需在调用代码之前加载
6. **KubeJS 重载**：修改配置后执行 `/kubejs reload`，新增载具还需 `/sbw_vehicle redeploy`
7. **状态机安全**：非法的状态转移会被 `transitionState()` 拒绝并记录警告日志，属于正常防护行为
8. **调试建议**：查阅 `logs/kubejs/` 下的日志，注意 `[SBW载具]` 前缀的输出
