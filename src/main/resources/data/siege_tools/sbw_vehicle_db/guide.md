# 载具数据库 — 填写指南（v2）

## 目录结构

```
kubejs/data/sbw_vehicle_db/
├── _registry.json                          ← 注册文件（分类 + 文件列表）
├── _ammo_types.json                        ← 弹药类型定义
├── guide.md                                ← 本文件
│
├── main_battle_tank/                       ← 分类目录
│   ├── superbwarfare--m_1a_2.json          ← 载具文件: <模组>--<基础名>.json
│   ├── mcsp--m1a2.json
│   └── ...
├── infantry_fighting_vehicle/
├── artillery/
├── air_defense/
├── aircraft/
├── helicopter/
├── defense_turret/
├── drone/
├── naval/
└── utility_vehicle/
```

**规则：**
- 每个分类是一个目录，名称匹配 `_registry.json` 中的 key
- 载具文件平放在分类目录下（无子分类）
- 文件命名: `<模组前缀>--<基础名>.json`

---

## 一、`_registry.json` — 分类注册

声明所有分类及其载具文件列表。加载器只依赖此文件发现数据。

```json
{
  "version": 2,
  "categories": {
    "main_battle_tank": {
      "enabled": true,
      "displayName": "主战坦克",
      "description": "主战坦克",
      "files": [
        "superbwarfare--m_1a_2.json",
        "mcsp--m1a2.json"
      ]
    },
    "helicopter": {
      "enabled": true,
      "displayName": "直升机",
      "description": "直升机",
      "files": [
        "superbwarfare--ah_6.json"
      ]
    }
  }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `enabled` | ✗ | 默认 `true`，设为 `false` 跳过整个分类 |
| `displayName` | 推荐 | 中文显示名（管理界面用） |
| `description` | 推荐 | 分类描述 |
| `files` | ✓ | 文件路径，相对于分类目录 |

> **所有文件路径相对于分类目录**：`superbwarfare--m_1a_2.json` → `<分类>/superbwarfare--m_1a_2.json`。

---

## 二、`_ammo_types.json` — 弹药类型注册

定义弹药补给系统识别的所有弹药类型。

```json
{
  "version": 1,
  "ammoTypes": {
    "large_shell_ap": {
      "id": "superbwarfare:large_shell_ap",
      "displayName": "§6大口径AP弹",
      "enName": "Large AP Shell",
      "maxStack": 64
    },
    "missile": {
      "id": "superbwarfare:missile",
      "displayName": "§a导弹",
      "enName": "Missile",
      "maxStack": 8
    }
  }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| Key（短名） | ✓ | 如 `large_shell_ap`，作为内部引用标识 |
| `id` | ✓ | 完整 Minecraft 物品 ID，如 `superbwarfare:large_shell_ap` |
| `displayName` | ✓ | 带颜色代码的中文显示名（支持 § 颜色码） |
| `enName` | ✓ | 英文显示名（不需要颜色码） |
| `maxStack` | ✓ | 该弹药物品的最大堆叠数 |

> 载具文件中 `weapons[].ammoTypes` 和 `ammoSlots` 引用的所有弹药 ID 都必须在此注册。

---

## 三、载具 JSON 文件 — `分类名/*.json`

每辆载变体一个独立 JSON 文件，存放在分类目录下。

```json
{
  "vehicleId": "superbwarfare:m_1a_2",
  "mod": "superbwarfare",
  "category": "main_battle_tank",
  "baseName": "m_1a_2",
  "displayType": "Tank",
  "maxHealth": 500,
  "maxEnergy": 10000000,
  "hasDecoy": true,
  "engineType": "Track",
  "mass": 70,
  "upStep": 2.25,
  "containerType": "Medium",
  "hudType": "@Land",
  "parts": [
    "WheelRight", "WheelLeft", "MainEngine", "Turret"
  ],
  "seatCount": 5,
  "weapons": [
    {
      "key": "Cannon",
      "displayKey": "Cannon",
      "ammoTypes": ["superbwarfare:large_shell_ap", "superbwarfare:large_shell_he"],
      "magazine": 1,
      "rpm": null,
      "damage": 700,
      "hasOverride": true
    },
    {
      "key": "MachineGun",
      "displayKey": "MachineGun",
      "ammoTypes": ["superbwarfare:rifle_ammo"],
      "magazine": 1,
      "rpm": 600,
      "damage": 9.5,
      "hasOverride": false
    }
  ],
  "nbtTemplate": {
    "Energy": 10000000,
    "Health": 500,
    "WeaponState": { "Cannon": { "components": { "minecraft:custom_data": { "GunData": { "Ammo": 1 } } } } },
    "Inventory": {
      "Items": [
        { "Slot": 0, "count": 32, "id": "superbwarfare:large_shell_ap" },
        { "Slot": 1, "count": 32, "id": "superbwarfare:large_shell_he" }
      ]
    }
  },
  "ammoSlots": {
    "superbwarfare:large_shell_ap": 32,
    "superbwarfare:large_shell_he": 32,
    "superbwarfare:rifle_ammo": 192
  }
}
```

### 顶层字段说明

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `vehicleId` | ✓ | string | 唯一标识，如 `superbwarfare:m_1a_2`，全局唯一 |
| `mod` | ✓ | string | 模组来源：`superbwarfare`、`mcsp` 等 |
| `category` | ✓ | string | 必须匹配 `_registry.json` 中的某个分类 key |
| `baseName` | ✓ | string | 简短内部 ID（不含命名空间） |
| `displayType` | ✓ | string | 显示分类：`Tank`、`IFV`、`Artillery` 等 |
| `maxHealth` | ✓ | int | 载具最大生命值 |
| `maxEnergy` | ✓ | int | 载具最大能量/电力 |
| `hasDecoy` | ✓ | bool | 是否能部署诱饵弹 |
| `engineType` | ✓ | string | `Track`（履带）、`Wheel`（轮式）、`Rotor`（旋翼）、`Plane`（固定翼） |
| `mass` | ✓ | number | 载具质量（吨），影响物理 |
| `upStep` | ✓ | number | 最大可爬越高度 |
| `containerType` | ✗ | string/null | 容器库存类型：`"Medium"`、`"Large"` 或 `null` |
| `hudType` | ✓ | string | HUD 标识：`@Land`、`@Air`、`@Sea`、`@Drone`、`@Turret` |
| `parts` | ✓ | string[] | 载具可损坏部件（影响瘫痪机制） |
| `seatCount` | ✓ | int | 座位数（驾驶员 + 乘客） |
| `weapons` | ✓ | array | 武器系统定义 |
| `nbtTemplate` | ✓ | object | 载具实体默认 NBT |
| `ammoSlots` | ✓ | object | 弹药库存容量：`{ "弹药ID": 最大数量 }` |

### 武器条目字段说明

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `key` | ✓ | string | 武器槽位 key，如 `Cannon`、`MachineGun` |
| `displayKey` | ✓ | string | 显示名称 key（用于翻译） |
| `ammoTypes` | ✓ | string[] | 可用的弹药 ID 列表（必须在 `_ammo_types.json` 中注册） |
| `magazine` | ✓ | int | 弹匣容量（1=单发，>1=自动装填） |
| `rpm` | ✗ | int/null | 射速（发/分钟）。`null`=单发 |
| `damage` | ✓ | number | 单发基础伤害 |
| `hasOverride` | ✓ | bool | 是否可鼠标瞄准覆盖 |

### NBT 模板说明

`nbtTemplate` 提供载具实体生成时的默认 NBT 数据，包括：

- **`Health`** / **`Energy`** — 初始值（通常等于最大值）
- **`WeaponState`** — 各武器初始状态，含 `GunData.Ammo`（已装弹数）
- **`Inventory.Items`** — 初始库存内容。Slot 编号按载具类型定义
- **部件健康度字段** — 如 `RightWheelHealth`、`TurretHealth`、`MainEngineHealth`
- **部件损坏标记** — 如 `RightWheelDamaged`、`TurretDamaged`

### 引擎类型参考

| 值 | 说明 | 示例 |
|-------|------|------|
| `Track` | 履带式 | 坦克、步兵战车 |
| `Wheel` | 轮式 | 卡车、通用车辆 |
| `Rotor` | 旋翼式 | 直升机 |
| `Plane` | 固定翼 | 飞机 |
| `Hover` | 气垫式 | 水上气垫船 |

### HUD 类型参考

| 值 | 说明 |
|-------|------|
| `@Land` | 陆战载具 HUD |
| `@Air` | 飞行载具 HUD |
| `@Sea` | 海战载具 HUD |
| `@Drone` | 无人机 HUD |
| `@Turret` | 固定炮塔 HUD |

### 分类参考

| 分类 key | 说明 |
|-------------|------|
| `main_battle_tank` | 主战坦克 |
| `infantry_fighting_vehicle` | 步兵战车/装甲运兵车 |
| `artillery` | 火炮/火箭炮 |
| `air_defense` | 防空单位 |
| `aircraft` | 固定翼飞机 |
| `helicopter` | 直升机 |
| `defense_turret` | 固定防御炮塔 |
| `drone` | 无人机 |
| `naval` | 海军载具 |
| `utility_vehicle` | 通用车辆/卡车 |

---

## 四、新增载具

1. 在对应分类目录下创建 JSON 文件，如 `main_battle_tank/mcsp--new_tank.json`
2. 将文件名添加到 `_registry.json` 对应分类的 `files` 数组中
3. 如果载具使用新弹药类型，在 `_ammo_types.json` 中注册
4. 执行 `/kubejs reload` 重载脚本

---

## 五、新增分类

1. 在 `sbw_vehicle_db/` 下创建新目录，如 `recon_vehicle/`
2. 在 `_registry.json` 的 `categories` 中添加新条目：
   ```json
   "recon_vehicle": {
     "enabled": true,
     "displayName": "侦察车",
     "description": "侦察车",
     "files": []
   }
   ```
3. 将载具 JSON 文件放入新目录，并在 `files` 中列出
4. 无需改加载器代码 — 自动发现读取 `_registry.json`

---

## 六、注意事项

1. **`_registry.json` 是唯一入口** — 未列出的文件不会被加载
2. **`vehicleId` 必须全局唯一** — 所有分类文件之间不可重复
3. **`weapons[].ammoTypes` 和 `ammoSlots` 中的弹药 ID** 必须在 `_ammo_types.json` 中注册
4. **载具 JSON 中的 `category`** 必须精确匹配 `_registry.json` 中的 key
5. **修改后执行 `/kubejs reload`** 重载所有脚本
6. **文件编码**：UTF-8 without BOM
7. **`enabled: false`** 会跳过整个分类
