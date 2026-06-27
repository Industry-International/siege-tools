# 启动脚本配置（Startup）使用教程

## 概述

启动脚本在**游戏启动时**加载，负责：
- 注册自定义物品（职业选择器、队伍选择器）
- 覆写方块属性（海战平衡——爆炸抗性）
- 定义全局变量（TAOV 飞机武器参数、修复零件）

## 目录结构

```
startup_scripts/
├── src/item/
│   ├── profession_item.js          ← 注册职业选择器物品
│   └── team_selector_item.js       ← 注册队伍选择器物品
├── naval_balance_block_properties.js  ← 海战平衡方块爆炸抗性
├── taov_returned_autocannon.js     ← TAOV 飞机武器参数
└── jsconfig.json                   ← 编辑器配置
```

---

## 一、物品注册

### 注册的自定义物品

| 物品 ID | 注册文件 | 翻译键 | 用途 |
|---------|---------|--------|------|
| `kubejs:profession_selector` | `profession_item.js` | `item.kubejs.profession_selector` | 右键打开职业选择 GUI |
| `kubejs:team_selector` | `team_selector_item.js` | `item.kubejs.team_selector` | 右键打开队伍选择 GUI |

### 物品属性

- 最大堆叠：**1**
- 纹理：`assets/kubejs/textures/item/{profession_selector,team_selector}.png`
- 模型：`assets/kubejs/models/item/{profession_selector,team_selector}.json`

### 代码示例

```js
// profession_item.js
StartupEvents.registry('item', event => {
  event.create('profession_selector')
    .translationKey('item.kubejs.profession_selector')
    .maxStackSize(1)
    .texture('kubejs:item/profession_selector')
})
```

### 翻译键

| 键 | 中文 | English |
|----|------|---------|
| `item.kubejs.profession_selector` | 职业选择器 | Profession Selector |
| `item.kubejs.team_selector` | 队伍选择器 | Team Selector |

---

## 二、海战平衡方块属性

### 功能

覆写特定方块的**爆炸抗性**，平衡海战中的舰船装甲强度。

### 代码位置

`startup_scripts/naval_balance_block_properties.js`

### 方块分组与爆炸抗性

| 组名 | 覆盖的方块 | 爆炸抗性 | 说明 |
|------|-----------|---------|------|
| **LOCOMETAL_NORMAL** | `railways:*_plated_locometal`、`*_flat_slashed_locometal`、`*_flat_riveted_locometal`（含16色） | **21** | 普通列车金属板 |
| **LOCOMETAL_ANTIBLAST** | `railways:*_brass_wrapped_locometal`、`*_copper_wrapped_locometal`、`*_iron_wrapped_locometal`（含16色） | **32** | 防爆列车金属板 |
| **LOCOMETAL_ANTIPEN** | `railways:*_slashed_locometal`、`*_riveted_locometal`、`*_locometal_pillar`、`*_locometal_smokebox`（含16色） | **17** | 防穿透列车金属板 |
| **CREATE_CASINGS** | `create:*_casing`（6种外壳） | **14** | Create 外壳 |
| **NETHERITE_BLOCK** | `minecraft:netherite_block` | **4** | 下界合金块（原版为1200，大幅降低） |

### 颜色列表

所有 Railways 方块支持 16 色染色变体：

```
white, light_gray, gray, black, brown, red, orange, yellow,
lime, green, cyan, light_blue, blue, purple, magenta, pink
```

### 数据包配合

对应的 block_armor tag 定义在 `data/` 目录下：
- `data/railways/block_armor/tags/locometal_normal.json`
- `data/railways/block_armor/tags/locometal_antiblast.json`
- `data/railways/block_armor/tags/locometal_antipen.json`
- `data/create/block_armor/tags/naval_casings.json`

---

## 三、TAOV 飞机武器参数

### 功能

定义 TAOV Returned 模组的飞机武器运行时参数。

### 代码位置

`startup_scripts/taov_returned_autocannon.js`

### 全局变量

```js
global.taovReturnedAutocannon = {
  muzzleVelocityBlocksPerSecond: 200,  // 弹丸初速（方块/秒），200 = 10 方块/tick
  forceTracer: false,                  // 是否强制启用曳光弹
  propellerSoundEnabled: true,         // 是否启用螺旋桨音效
  rocketRackReloadSeconds: 20,         // 火箭弹发射架装填时间（秒）
  kamikazeExplosivePower: 12           // 神风自爆威力
}
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `muzzleVelocityBlocksPerSecond` | `200` | 弹丸初速，单位方块/秒（200 = 10 方块/tick） |
| `forceTracer` | `false` | 是否强制所有子弹带曳光效果 |
| `propellerSoundEnabled` | `true` | 是否启用螺旋桨音效 |
| `rocketRackReloadSeconds` | `20` | 火箭弹发射架重新装填所需秒数 |
| `kamikazeExplosivePower` | `12` | 神风自爆的爆炸威力 |

---

## 四、TAOV 修复零件

### 功能

定义 TAOV Returned 修复零件的每零件修复方块数。

### 代码位置

`server_scripts/taov_returned_repair_parts.js`

```js
const TAOV_RETURNED_BLOCKS_PER_REPAIR_PART = 1
```

服务器启动时自动执行：
```
taov_returned_repair_parts blocks_per_part 1
```

### 配置说明

| 参数 | 值 | 说明 |
|------|-----|------|
| `TAOV_RETURNED_BLOCKS_PER_REPAIR_PART` | `1` | 每个修复零件可修复 1 个方块 |

> 此参数通过 `ServerEvents.loaded` 事件在服务器加载时自动设置，无需手动操作。

---

## 五、修改后刷新

启动脚本修改后需要**重启服务器**才能生效（`/kubejs reload` 对 startup_scripts 无效）。

---

## 六、与其他模块的关系

| 模块 | 关系说明 | 文档 |
|------|---------|------|
| **职业系统** | 使用 `kubejs:profession_selector` 物品 | [职业系统](./profession-使用教程.md) |
| **队伍选择器** | 使用 `kubejs:team_selector` 物品 | [队伍选择器](./team-使用教程.md) |
| **数据包** | 海战平衡需要 `data/` 目录下的 block_armor tag 配合 | — |
