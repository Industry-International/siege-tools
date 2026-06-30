# SBW 载具数据库数据包书写指南

## 目录结构

```
sbw_vehicle_db/
  ├── _registry.json              ← 分类注册表
  ├── _ammo_types.json            ← 弹药类型定义
  ├── infantry_fighting_vehicle/  ← 分类目录（自动发现）
  │   └── bmp_2.json              ← 单辆载具数据
  ├── main_battle_tank/
  │   └── t_90a.json
  └── ...
```

**规则：**
- 每个分类一个目录，目录名 = 分类 key
- 每辆载具一个 `.json` 文件
- `_` 开头的文件和目录为元数据，会被自动跳过
- 无需在 `_registry.json` 中声明文件列表，目录自动发现

---

## `_registry.json` — 分类注册表

```json
{
  "formatVersion": 1,
  "categories": {
    "infantry_fighting_vehicle": {
      "displayName": "步兵战车/装甲车",
      "description": "Infantry Fighting Vehicles & APCs"
    }
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `formatVersion` | int | ✅ | 格式版本号，固定为 1 |
| `categories` | object | ✅ | 分类字典 |
| `categories.<key>.displayName` | string | ✅ | 分类显示名（支持 § 颜色码） |
| `categories.<key>.description` | string | ❌ | 分类英文描述 |

---

## `_ammo_types.json` — 弹药类型注册表

```json
{
  "formatVersion": 1,
  "ammoTypes": {
    "large_shell_ap": {
      "id": "superbwarfare:large_shell_ap",
      "displayName": "§6大口径AP弹",
      "enName": "Large AP Shell",
      "maxStack": 64
    }
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `formatVersion` | int | ✅ | 固定为 1 |
| `ammoTypes` | object | ✅ | 弹药类型字典 |
| `<shortName>.id` | string | ✅ | 完整物品 ID（`mod:id`） |
| `<shortName>.displayName` | string | ✅ | 显示名 |
| `<shortName>.enName` | string | ❌ | 英文名 |
| `<shortName>.maxStack` | int | ❌ | 最大堆叠（默认 64） |

---

## 车辆 JSON — 单辆载具数据

### 完整示例

```json
{
  "formatVersion": 1,
  "vehicleId": "superbwarfare:bmp_2",
  "mod": "superbwarfare",
  "displayType": "APC",
  "hudType": "@Land",
  "tags": ["ground", "tracked"],
  "stats": {
    "maxHealth": 300,
    "maxEnergy": 5000000,
    "mass": 14.6,
    "engineType": "Track",
    "containerType": "Medium",
    "seatCount": 7,
    "hasDecoy": true
  },
  "parts": ["WheelRight", "WheelLeft", "MainEngine", "Turret"],
  "weapons": [
    {
      "key": "Cannon",
      "ammoTypes": ["superbwarfare:small_shell_ap", "superbwarfare:small_shell_he", "superbwarfare:small_shell_gs"],
      "magazine": 1,
      "rpm": 250,
      "damage": 65
    }
  ],
  "defaultAmmo": {
    "superbwarfare:small_shell_ap": 32,
    "superbwarfare:small_shell_he": 32,
    "superbwarfare:medium_anti_ground_missile": 16
  },
  "spawnNbt": {
    "Health": 300,
    "Energy": 5000000,
    "RightWheelHealth": 100,
    "LeftWheelHealth": 100,
    "MainEngineHealth": 150,
    "TurretHealth": 100,
    "DecoyReady": 1
  }
}
```

### 字段说明

#### 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `formatVersion` | int | ✅ | 固定为 1 |
| `vehicleId` | string | ✅ | 唯一标识，格式 `mod:name` |
| `mod` | string | ❌ | 所属模组，方便人阅读 |
| `displayType` | string | ❌ | UI 显示用类型标签 |
| `hudType` | string | ❌ | HUD 显示类型，如 `@Land` |
| `tags` | string[] | ❌ | 标签，如 `["ground","tracked"]` |
| `stats` | object | ❌ | 属性组（见下方） |
| `parts` | string[] | ❌ | 载具部件列表 |
| `weapons` | Weapon[] | ❌ | 武器列表 |
| `defaultAmmo` | object | ❌ | 默认弹药配置 |
| `spawnNbt` | object | ❌ | spawn NBT 基础字段 |

#### `stats` 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| `maxHealth` | int | 最大生命值 |
| `maxEnergy` | int | 最大能量 |
| `mass` | double | 质量 |
| `upStep` | double | 越障高度 |
| `seatCount` | int | 座位数 |
| `containerType` | string | 容器类型 |
| `engineType` | string | 引擎类型 |
| `hasDecoy` | bool | 是否有诱饵弹 |

#### 武器对象 (Weapon)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `key` | string | ✅ | 武器 key |
| `displayKey` | string | ❌ | 显示名，以 `@` 开头则隐藏 |
| `ammoTypes` | string[] | ✅ | 可用弹药 ID 列表 |
| `magazine` | int | ❌ | 弹匣容量（默认 1） |
| `rpm` | int | ❌ | 射速（发/分钟） |
| `damage` | double | ❌ | 基础伤害 |

> **注意**：不要列出无弹药的武器（`ammoTypes: []`），这样的武器会在加载时自动过滤。

#### `defaultAmmo` 对象

键为完整物品 ID，值为数量：
```json
{
  "superbwarfare:small_shell_ap": 32,
  "superbwarfare:rifle_ammo": 192
}
```

加载时自动从此生成 `Inventory.Items`。

#### `spawnNbt` 对象

存放 `/summon` 命令中使用的 NBT 字段。**不需要**写 `Inventory` 和 `WeaponState`，它们会在加载时自动从 `defaultAmmo` 和 `weapons` 生成。

可选的字段示例：
```json
{
  "Health": 300,
  "Energy": 5000000,
  "Invulnerable": 0,
  "DecoyReady": 1,
  "RightWheelHealth": 100,
  "MainEngineHealth": 150
}
```

---

## 生成逻辑（加载时自动完成）

```
defaultAmmo        → 自动 → Inventory.Items（Slot 编号、count、id 自动排布）
weapons[]          → 自动 → WeaponState（每把枪默认 Ammo=1）
spawnNbt + 上面两个  → 自动 → 完整 spawnNBT（供 /summon 使用）
```

---

## 与旧格式的差异

| 字段 | 旧格式 | 新格式 | 原因 |
|------|--------|--------|------|
| `version` | `"version": 2` | `"formatVersion": 1` | 统一命名 |
| `category` | 有 | ❌ 去掉 | 从目录名推导 |
| `baseName` | 有 | ❌ 去掉 | 从 vehicleId 推导 |
| `stats.*` | 顶层字段 | 收进 `stats` 对象 | 结构化 |
| `ammoSlots` | 旧名 | ✅ `defaultAmmo` | 命名更清晰 |
| `nbtTemplate` | 含完整 NBT | ✅ `spawnNbt`，仅基础字段 | 去重 |
| `Inventory.Items` | 在 nbtTemplate 中 | ❌ 去掉，自动生成 | 单一数据源 |
| `WeaponState` | 每枪重复 | ❌ 去掉，自动生成 | 去重 |
| 空武器 | 列出 | ❌ 去掉 | 无意义 |
| `hasOverride` | 有 | ❌ 去掉 | 未使用 |
| `tags` | 无 | ✅ 新增 | 可扩展 |
