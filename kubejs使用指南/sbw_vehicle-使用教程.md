# SBW 卓越前线 — 载具自动部署系统

## 概述

本系统实现 SBW（Superb Warfare）模组载具的**自动部署**与**自动重生**。采用 **8 状态状态机**驱动，将复杂的补员逻辑拆分为多个独立状态，每个状态职责清晰、转移规则严格。

---

## 目录

- [功能特性](#功能特性)
- [状态机架构](#状态机架构)
  - [8 个状态](#8-个状态)
  - [状态流转图](#状态流转图)
  - [转移规则](#转移规则)
- [配置指南](#配置指南)
  - [基本配置项](#基本配置项)
  - [deployNBT 模板详解](#deploynbt-模板详解)
  - [每辆载具独立配置](#每辆载具独立配置)
  - [maxCount 最大数量控制](#maxcount-最大数量控制)
- [指令使用](#指令使用)
- [文件结构](#文件结构)
- [常见问题](#常见问题)

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 8 状态状态机 | `UNINITIALIZED` → `IDLE` → `WAITING_CHUNK` / `CHUNK_LOADED` → `DEPLOYED` → `TIMING`（重生倒计时）/ `OVER_CAPACITY`（超量）/ `UNDER_CAPACITY`（不足） |
| 严格转移校验 | 非法状态转移会被拒绝并记录警告日志 |
| 自动部署 | 系统启动后由补员循环自动检测并部署 |
| 独立标签 | 每辆载具携带唯一标签，存活期间不重复生成 |
| 自动重生 | 载具被摧毁后自动进入 `TIMING` 状态倒计时，结束即重生 |
| 智能区块检测 | `IDLE` → `WAITING_CHUNK`（区块未加载时等待），玩家靠近后自动恢复 |
| 模板化部署 | 部署时自动应用初始 NBT（能量、弹药、预装填、部件状态等） |
| 定期持久化 | 每 5 次补员循环自动全量保存一次，`remainingTicks` 频繁落盘，崩溃重启不丢数据 |
| 重启保底 | 启动补员循环时自动处理残留 TIMING 状态，置为就绪后立即部署 |
| 无残留清除 | clear/reset 使用 `discard()` 直接移除，不留掉落物 |
| 完全擦除 | `/sbw_vehicle clear` 不指定队伍时完全擦除 persistentData（含 active 标志） |
| maxCount 控制 | 每辆载具可设置最大存活数，超量自动标记 `OVER_CAPACITY` |

---

## 状态机架构

状态机定义在 `tools/state_machine.js` 中，核心代码为 `VEHICLE_STATE` 常量和 `transitionState()` 函数。

### 8 个状态

| 状态 | 值 | 含义 | 说明 |
|------|-----|------|------|
| `UNINITIALIZED` | `'uninitialized'` | 未初始化 | 首次创建或系统重置后的初始态 |
| `IDLE` | `'idle'` | 空闲 | 初始化完毕，等待部署条件满足 |
| `WAITING_CHUNK` | `'waiting_chunk'` | 等待区块加载 | 区块未加载，等待玩家靠近 |
| `CHUNK_LOADED` | `'chunk_loaded'` | 区块已加载 | 区块已就绪，可以执行部署 |
| `DEPLOYED` | `'deployed'` | 载具已部署 | 实体已生成并存活 |
| `OVER_CAPACITY` | `'over_capacity'` | 载具超量 | 当前存活数超过 `maxCount` |
| `UNDER_CAPACITY` | `'under_capacity'` | 载具不足 | 当前存活数未达 `maxCount` |
| `TIMING` | `'timing'` | 计时中 | 重生倒计时进行中 |

### 状态流转图

```
UNINITIALIZED ──→ IDLE ──→ WAITING_CHUNK ──→ CHUNK_LOADED ──→ DEPLOYED
                      ↑          ↑                 │               │
                      │          └─────────────────┘               │
                      │           TIMING ←─────────────────────────┘
                      │             │
                      │             └──→ WAITING_CHUNK / IDLE
                      │
                      └──←── OVER_CAPACITY ──→ DEPLOYED / IDLE
                      └──←── UNDER_CAPACITY ──→ IDLE / WAITING_CHUNK / TIMING
```

### 转移规则

| 当前状态 | 允许的下一个状态 | 触发条件 |
|----------|-----------------|----------|
| `UNINITIALIZED` | `IDLE` | 初始化完成 |
| `IDLE` | `WAITING_CHUNK` | 区块未加载 |
| `IDLE` | `CHUNK_LOADED` | 区块已加载 |
| `WAITING_CHUNK` | `CHUNK_LOADED` | 区块加载完成 |
| `WAITING_CHUNK` | `DEPLOYED` | 区块加载 + 直接部署（并列） |
| `WAITING_CHUNK` | `IDLE` | 系统复位/跳过 |
| `CHUNK_LOADED` | `DEPLOYED` | 执行部署 |
| `CHUNK_LOADED` | `OVER_CAPACITY` | 区块就绪但超量 |
| `CHUNK_LOADED` | `IDLE` | 部署跳过/取消 |
| `DEPLOYED` | `TIMING` | 载具被摧毁 |
| `DEPLOYED` | `OVER_CAPACITY` | 发现超出数量限制 |
| `DEPLOYED` | `IDLE` | 手动清除/重置 |
| `OVER_CAPACITY` | `DEPLOYED` | 超量已清除，回到正常 |
| `OVER_CAPACITY` | `CHUNK_LOADED` | 超量已清除，区块就绪 |
| `OVER_CAPACITY` | `IDLE` | 超量手动处理 |
| `UNDER_CAPACITY` | `IDLE` | 数量不足，重新部署 |
| `UNDER_CAPACITY` | `WAITING_CHUNK` | 数量不足+区块未加载 |
| `UNDER_CAPACITY` | `TIMING` | 不足中启动补员倒计时 |
| `TIMING` | `CHUNK_LOADED` | 计时完成且区块已加载 |
| `TIMING` | `WAITING_CHUNK` | 计时完成但区块未加载 |
| `TIMING` | `IDLE` | 计时完成（备选） |
| **任意状态** | `UNINITIALIZED` | 系统重置（强制） |

---

## 配置指南

配置文件：`server_scripts/sbw_vehicle/config.js`

### 基本配置项

```js
const SBW_VEHICLE_CONFIG = {
  persistKey: 'sbw_vehicle',      // 持久化数据存储键名
  tagPrefix: 'sbw_vehicle_',      // 实体标签前缀（用于追踪）
  checkInterval: 1,               // 补员检测间隔（tick，1=每 tick 检测一次）
}
```

### deployNBT 模板详解

每辆载具配置中的 `deployNBT` 定义了部署时应用的初始 NBT。以下为完整字段说明：

#### 核心属性

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `Energy` | int | 0 | 载具能量/电力。影响武器可用性，0=没电 |
| `Health` | float | 500.0 | 载具总生命值，归零则摧毁 |
| `Invulnerable` | 0/1 | 0 | 无敌模式，1=无法被伤害 |
| `IsWreck` | 0/1 | 0 | 是否残骸状态，1=已报废形态 |
| `Power` | float | 0.0 | 动力输出，影响移动速度 |

#### 部件健康度

每个部件有 `Health`（健康度）和 `Damaged`（是否损坏）两个字段。当 `Health` 归零时 `Damaged` 自动变为 1。

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `LeftWheelHealth` | float | 100.0 | 左轮健康度 |
| `LeftWheelDamaged` | 0/1 | 0 | 左轮是否损坏 |
| `RightWheelHealth` | float | 100.0 | 右轮健康度 |
| `RightWheelDamaged` | 0/1 | 0 | 右轮是否损坏 |
| `MainEngineHealth` | float | 150.0 | 主引擎健康度 |
| `MainEngineDamaged` | 0/1 | 0 | 主引擎是否损坏 |
| `SubEngineHealth` | float | 150.0 | 副引擎健康度 |
| `SubEngineDamaged` | 0/1 | 0 | 副引擎是否损坏 |
| `TurretHealth` | float | 100.0 | 炮塔健康度 |
| `TurretDamaged` | 0/1 | 0 | 炮塔是否损坏 |
| `TurretBurned` | 0/1 | 0 | 炮塔是否烧毁 |
| `TurretBurnTimer` | int | 0 | 炮塔燃烧计时（tick） |

#### 武器系统

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `DecoyReady` | 0/1 | 1 | 诱饵弹是否装填就绪 |
| `ChargeProgress` | float | 0.0 | 特殊武器充能进度（0.0~1.0） |

`WeaponState` 是嵌套的复合标签，结构如下：

```js
WeaponState: {
  Cannon: {                   // 主炮
    components: {
      "minecraft:custom_data": {
        GunData: { Ammo: 1 }  // Ammo = 预装填的炮弹数
      }
    }
  },
  MachineGun: {               // 同轴机枪
    components: {
      "minecraft:custom_data": {
        GunData: { Ammo: 200 }
      }
    }
  },
  PassengerMachineGun: {      // 乘客机枪
    components: {
      "minecraft:custom_data": {
        GunData: { Ammo: 200 }
      }
    }
  }
}
```

#### 弹药库存

```js
Inventory: {
  Items: [
    { Slot: 0,  count: 63, id: 'superbwarfare:large_shell_ap' },
    { Slot: 1,  count: 64, id: 'superbwarfare:large_shell_he' },
    // 更多弹药...
  ]
}
```

`Slot` 编号范围：`0~53`（共 54 格）

#### 弹药类型参考

| 物品 ID | 说明 |
|---------|------|
| `superbwarfare:large_shell_ap` | 大口径 AP 弹（穿甲弹） |
| `superbwarfare:large_shell_he` | 大口径 HE 弹（高爆弹） |
| `superbwarfare:small_shell_ap` | 小口径 AP 弹 |
| `superbwarfare:small_shell_he` | 小口径 HE 弹 |
| `superbwarfare:rifle_ammo` | 步枪弹（机枪用） |
| `superbwarfare:heavy_ammo` | 重弹 |
| `superbwarfare:missile` | 导弹 |
| `superbwarfare:rocket` | 火箭弹 |

#### 特殊 NBT 类型提示

大多数字段直接用 JS 数字/布尔即可。如需指定特定 NBT 类型：

```js
// 例如需要明确存为 ByteTag
SomeField: { __nbt_type: "byte", value: 1 }

// 支持的类型: byte, short, long, float, double
```

### 每辆载具独立配置

每辆载具在 `teams` 下独立配置，`deployNBT` 写在该载具内部：

```js
teams: {
  attack: {
    vehicles: [
      {
        id: 'attack_tank_1',           // 唯一标识符
        vehicleType: 'superbwarfare:t_90a',  // 载具实体类型
        pos: [0.5, 64, 0.5, 90, 0],    // [x, y, z, yaw, pitch]
        respawnDelay: 1200,             // 重生延迟（tick，1200=60秒）
        maxCount: 1,                    // （可选）最大同时存活数
        deployNBT: {                    // ↓↓↓ 部署模板 ↓↓↓
          Energy: 10000000,
          Health: 500.0,
          // ... 更多字段见上方参考
        }
      },
      // 更多载具...
    ]
  }
}
```

**关键规则：**
- `deployNBT` 不写或设为 `null` → **白板生成**（无能量、无弹药）
- 每辆载具的 `deployNBT` **完全独立**，修改一辆不影响其他
- 每辆载具可配置不同的 `respawnDelay`

### maxCount 最大数量控制

| 功能 | 说明 |
|------|------|
| 部署检查 | 部署前扫描世界存活数，已达上限则跳过生成 |
| 状态机检测 | `DEPLOYED` 状态自动检查，超量标记 `OVER_CAPACITY` |
| 自动恢复 | 超量清除后自动回到 `DEPLOYED` |
| 跨队隔离 | 不同队伍的同名 ID 通过队伍前缀隔离 |

> 不设置或设为 `0` 表示不限制数量。

---

## 指令使用

| 指令 | 说明 | 权限 |
|------|------|------|
| `/sbw_vehicle start` | **激活系统** + 初始化状态 + **启动补员循环** | OP 2 |
| `/sbw_vehicle stop` | **停用系统** + 停止补员循环 + 清除所有载具 | OP 2 |
| `/sbw_vehicle deploy [<队伍>]` | 部署指定/所有队伍的载具（使用状态机） | OP 2 |
| `/sbw_vehicle redeploy` | 强制重新部署（清旧+重部署） | OP 2 |
| `/sbw_vehicle reset` | 重置所有载具状态 | OP 2 |
| `/sbw_vehicle clear [<队伍>]` | 清除载具实体+重置状态 | OP 2 |
| `/sbw_vehicle status` | 查看各载具状态（适配全部8个状态） | OP 2 |
| `/sbw_vehicle timelist` | 查看所有补员倒计时列表 | OP 2 |

### 状态指示说明（ActionBar / Status）

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

## 文件结构

| 文件 | 说明 |
|------|------|
| `server_scripts/sbw_vehicle/config.js` | 载具配置（队伍、坐标、deployNBT 模板） |
| `server_scripts/sbw_vehicle/main.js` | 模块入口（全局常量 + 死亡事件监听） |
| `server_scripts/sbw_vehicle/replenish.js` | **自动补员系统（状态机驱动）** |
| `server_scripts/sbw_vehicle/command.js` | 指令注册（7 个命令） |
| `server_scripts/sbw_vehicle/tools/a_java_refs.js` | Java 类引用 |
| `server_scripts/sbw_vehicle/tools/log.js` | 日志工具 |
| `server_scripts/sbw_vehicle/tools/nbt.js` | JSON → NBT 转换 |
| `server_scripts/sbw_vehicle/tools/persist.js` | 持久化数据 + 系统开关 |
| `server_scripts/sbw_vehicle/tools/entity.js` | 实体查找 / 区块检测 / 清理 |
| `server_scripts/sbw_vehicle/tools/deploy.js` | 载具部署（使用状态机） |
| `server_scripts/sbw_vehicle/tools/misc.js` | 杂项（ID提取 / 配置查找 / 清除） |
| `server_scripts/sbw_vehicle/tools/status.js` | 状态查询 & ActionBar（适配8状态） |
| `server_scripts/sbw_vehicle/tools/state_machine.js` | **状态机核心（8状态枚举 + 转移规则）** |
| `assets/kubejs/lang/zh_cn.json` | 中文语言文件 |
| `assets/kubejs/lang/en_us.json` | 英文语言文件 |

---

## 常见问题

### Q: 载具生成后没有能量/弹药

A: 检查该载具配置中是否写了 `deployNBT`。不写 = 白板生成。参考上方 `attack_tank_1` 的模板。

### Q: 载具被摧毁后没有重生

A: 检查：1) `respawnDelay` 是否大于 0；2) 系统是否已激活（`/sbw_vehicle start`）；3) 补员循环是否运行中；4) 控制台是否有报错。

### Q: 如何调整重生速度？

A: 修改该载具的 `respawnDelay` 值（单位：tick，20tick=1秒）。例如 `respawnDelay: 600` = 30 秒重生。

### Q: 如何让不同载具有不同的弹药配置？

A: 每辆载具的 `deployNBT.Inventory.Items` 独立配置，互不影响。

### Q: clear/reset 后地上有掉落物

A: 已修复。现在 clear/reset 使用 `discard()` 替代 `kill()`，载具直接消失不留掉落物。

### Q: 状态显示为 `⚠`（警告）是什么意思？

A: 表示该载具处于 `OVER_CAPACITY` 状态，当前存活数超过了 `maxCount` 限制。系统会自动检测超量清除后恢复。

### Q: 怎样重新加载配置？

A: 执行 `/kubejs reload` 重新加载脚本。如果是新加的载具，还需重新部署：`/sbw_vehicle redeploy`。

### Q: 状态机报 "非法转移" 警告怎么办？

A: 这是正常的安全防护。非法转移会被记录在日志中（`logs/kubejs/`），帮助你调试不正确的状态流转。如果确实需要强制设状态，可使用 `forceSetState()`（仅限系统级重置）。

---

> 详细 NBT 字段参考请直接查看 `server_scripts/sbw_vehicle/config.js` 中的注释手册。
> 状态机 API 参考请查看 `server_scripts/sbw_vehicle/tools/state_machine.js`。
> 补员循环逻辑请查看 `server_scripts/sbw_vehicle/replenish.js`。
