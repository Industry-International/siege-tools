# 职业系统（Profession）使用教程

## 目录结构

```
server_scripts/
├── profession/                          ← 职业系统
│   ├── profession_gui.js                ← GUI 交互（职业选择、武器配置、配件改装、给予装备）
│   ├── profession_backpack.js           ← 背包系统（5槽位/职业，保存加载武器+配件配置）
│   ├── profequip_cmd.js                 ← /profequip 指令（装备发放 + 容器守卫 + 标签守卫）
│   ├── kubejsadmin_cmd.js               ← /kubejsadmin 指令（管理员清空）
│   └── no_job_tag.js                    ← 无职业标签自动管理（登录自动标记 + 选/取消职业联动）
│   ├── config/
│   │   └── a_tacz_config.js             ← 共享工具（Java 类加载、GUI 布局、配件持久化）
│   └── prof_configs/
│       ├── b_tacz_prof_assault.js       ← 突击兵全套配置（同上）
│       ├── b_tacz_prof_medic.js         ← 医疗兵全套配置（同上）
│       ├── b_tacz_prof_scout.js         ← 侦察兵全套配置（同上）
│       ├── b_tacz_prof_support.js       ← 支援兵全套配置（同上）
│       └── z_tacz_config_build.js       ← 汇总构建（最后加载，生成查表函数）
└── team/                                   ← 队伍选择器（独立模块）
    └── team_selector_gui.js                ← 详见 [team-使用教程.md](./team-使用教程.md)
```

加载顺序（由文件名前缀控制）：
1. `config/a_tacz_config.js` — 基础工具 + PROF_CONFIGS 容器
2. `prof_configs/b_tacz_prof_*.js` — 各职业**完整配置**（TACZ + 非TACZ + 护甲 + 额外物品）
3. `prof_configs/z_tacz_config_build.js` — 汇总查表 + getProfConfig + getProfessionWeaponList
4. `profequip_cmd.js` — 装备发放 + 守卫逻辑
5. `profession_backpack.js` — 背包系统函数
6. `no_job_tag.js` — 无职业标签自动管理（独立模块，不依赖其他函数）
7. `profession_gui.js` — GUI 交互
8. `kubejsadmin_cmd.js` — 管理指令
9. `team/team_selector_gui.js` — 队伍选择器（详见独立文档）

---

## 一、GUI 操作流程

```
右键职业选择器
    │
    ├── 已选择职业 → 直接进入【武器配置页】
    └── 未选择职业 → 进入【职业选择页】
                        │
                    选择职业（突击兵/侦察兵/医疗兵/支援兵）
                        │
                        ▼
                   【武器配置页】 ←── 左上角「取消选择职业」可返回
                   ┌──────────────────────┐
                   │ 主武器 │ 副武器 │ 特殊武器 │  ← 左键进入选择列表
                   │    (已选显示实际武器)     │  ← 右键清空该选择
                   ├──────────────────────┤
                   │ ⬆加载背包 │ ★给予装备 │ ⬇保存背包 │
                   └──────────────────────┘
                        │
                    选择武器 → 返回武器配置页
                        │
                    TACZ 枪械可右键打开【配件改装】
```

### 武器配置页按钮一览

| 位置 | 按钮 | 功能 |
|------|------|------|
| (0,0) | `§c← 取消选择职业` | 清空职业+武器选择，返回职业选择页 |
| (2,3) | 主武器图标 | 左键进入主武器列表，右键清空主武器 |
| (4,3) | 副武器图标 | 左键进入副武器列表，右键清空副武器 |
| (6,3) | 特殊武器图标 | 左键进入特殊武器列表，右键清空特殊武器 |
| (0,5) | `⬆ 加载背包` | 从已保存的背包槽位加载武器配置 |
| **(4,5)** | **`§a✔ 给予装备`** | **清空背包 → 发放全套职业装备（护甲+武器+弹药+配件+额外物品）** |
| (8,5) | `⬇ 保存到背包` | 将当前武器配置保存到背包槽位 |
| (8,0) | `✖ 删除背包` | 删除指定背包槽位的数据 |

> 点击 **"给予装备"** 会先关闭 GUI（等待客户端库存快照恢复，约 0.1 秒），然后执行完整的装备发放流程（与 `/profequip give` 指令逻辑一致）。
> 这种延迟发放机制避免了 Minecraft GUI 关闭时库存快照覆盖已发放物品的问题。

### 配件改装页按钮一览

| 位置 | 按钮 | 功能 |
|------|------|------|
| (0,0) | `§c← 返回` | 返回上级武器选择列表 |
| (8,0) | `§c✖ 退出` | 直接关闭 GUI |
| (0,5) | `§a✔ 保存配件` | **保存当前配件配置，保存后自动返回上级菜单** |
| (8,5) | `§c✖ 清空所有配件` | 清空该枪械所有配件 |

### 各页面标题规则

| 页面 | 标题格式 |
|------|---------|
| 职业选择页 | `§8职业选择` |
| 武器配置页 | `§c突击兵 §8武器配置` |
| 主武器列表 | `§c突击兵 §8主武器配置界面` |
| 副武器列表 | `§c突击兵 §8副武器配置界面` |
| 特殊武器列表 | `§c突击兵 §8特殊武器配置界面` |
| 配件改装 | 保持默认（枪械名 + 配件配置） |

---

## 二、武器类型

### 1. TACZ 枪械

使用 `tacz:modern_kinetic_gun` 物品 + GunId NBT 区分。

> **枪械数据查询**：所有可用枪械的 GunId、弹药、支持配件槽位请查阅 **[tacz枪械配件数据-使用指南.md](./tacz枪械配件数据-使用指南.md)** 及对应的枪包数据文件。

**配置文件示例（`b_tacz_prof_assault.js` — 完整结构）：**

```js
PROF_CONFIGS.assault = {
  // ===== TACZ 枪械 =====
  guns: {
    primary: {                    // 主武器
      ak47: {                     // 武器 ID（内部标识）
        gunId: 'tacz:ak47',       // TACZ GunId
        GunFireMode: 'AUTO',
        GunCurrentAmmoCount: 30,
        ammo: {                   // 弹药配置
          ammoId: 'tacz:762x39',  // 弹药类型
          main: 210,              // 主武器备弹
          level: 2,               // 弹药等级（有 level → 弹药盒）
        },
        attachments: {            // 可用配件列表
          scope: [{id:'tacz:scope_reflex'}, ...],
          muzzle: [{id:'tacz:muzzle_silencer_knight_qd'}, ...],
          stock: [{id:'tacz:stock_heavy'}, ...],
        },
      },
    },
    secondary: { /* 副武器，同上 */ },
    tertiary: {},  // 特殊武器（非 TACZ 时 guns 可以为空）
  },
  weapons: {
    primary:   ['ak47', 'scar_l'],   // 主武器可选列表
    secondary: ['mars'],             // 副武器可选列表
    tertiary:  ['snowball'],         // 特殊武器可选列表
  },

  // ===== 非 TACZ 武器（可选）=====
  nonTaczDisplay: {
    snowball: { item: 'minecraft:snowball', i18n: true },
  },
  nonTaczAmmo: {
    snowball: { item: 'minecraft:snowball', count: 16 },
  },

  // ===== 护甲 =====
  armor: [
    'minecraft:iron_boots',
    'minecraft:iron_leggings',
    'minecraft:iron_chestplate',
    'minecraft:iron_helmet',
  ],

  // ===== 额外物品 =====
  extras: [
    { item: 'minecraft:cooked_beef', count: 32 },
  ],
}
```

### 2. 非 TACZ 武器（特殊武器）

在 **`b_tacz_prof_xxx.js` 内** 用 `nonTaczDisplay` 表映射为实际物品。

**配置示例（含弹药）：**

```js
// b_tacz_prof_assault.js（与 guns/weapons 同级）
PROF_CONFIGS.assault = {
  // ... TACZ guns + weapons ...

  // ===== 非 TACZ 武器 =====
  nonTaczDisplay: {
    snowball: { item: 'minecraft:snowball', i18n: true },  // 有 i18n → 用 KubeJS 翻译键
  },
  nonTaczAmmo: {
    snowball: { item: 'minecraft:snowball', count: 16 },
  },
}
```

> 所有职业的 `nonTaczDisplay` / `nonTaczAmmo` 在 `z_tacz_config_build.js` 加载时自动合并为全局查表。
> 如需新增非 TACZ 武器，只需在对应职业的 `b_tacz_prof_xxx.js` 中添加即可。

### i18n 标记说明

`VANILLA_WEAPON_DISPLAY` 中每条记录的 `i18n` 字段控制名称来源：

| i18n | 名称来源 | 需要语言文件？ | 适用场景 |
|------|---------|--------------|---------|
| 未设置 / `false` | 物品自身内置名（模组/原版） | 不需要 | `superbwarfare:sentinel`（模组自带"哨兵狙击步枪"） |
| `true` | `offhand.kubejs.<id>` 翻译键 | 需要 | `minecraft:snowball`（原版雪球，需要覆写为"雪球"） |

---

## 三、弹药系统

### TACZ 弹药

在 `b_tacz_prof_*.js` 的 `guns` 中配置：

```js
// 有 level → 发放弹药盒（tacz:ammo_box）
ammo: { ammoId: 'tacz:762x39', main: 210, level: 2 }

// 无 level → 直接发弹药物品（tacz:762x39 × 210）
ammo: { ammoId: 'tacz:762x39', main: 210 }
```

### 非 TACZ 弹药

在 `b_tacz_prof_*.js` 的 `nonTaczAmmo` 中配置（与 TACZ 弹药的 `guns.ammo` 路径不同）：

```js
// 在职业配置的 nonTaczAmmo 中定义
nonTaczAmmo: {
  sentinel: { item: 'superbwarfare:sniper_ammo', count: 30 },
  snowball: { item: 'minecraft:snowball',        count: 16 },
}
```

> 超堆叠上限时自动拆分：比如 `count: 120` 但物品最大堆叠 99，会分为 99 + 21 两组发放。

---

## 四、背包系统

每个职业独立拥有 **5 个背包槽位**，每位玩家数据独立。背包可保存**武器配置 + 配件配置**，用于快速切换不同方案。

### 操作入口

在武器配置页底部：

| 按钮 | 操作 |
|------|------|
| `⬆ 加载背包`（左下角） | 选择一个已保存的槽位，加载其武器配置到当前选择 |
| `⬇ 保存到背包`（右下角） | 将当前选择的武器+配件配置保存到一个槽位 |
| `✖ 删除背包`（右上角） | 选择一个有数据的槽位删除 |

### 数据保存内容

每个背包槽位保存以下数据：
- `mainWeapon` — 主武器 ID
- `offhandWeapon` — 副武器 ID
- `specialWeapon` — 特殊武器 ID
- `attachments` — 所有已配置的 TACZ 配件（完整副本）

### 背包槽位界面

```
┌─────────────────────────────────┐
│  ← 返回              §8选择要保存的背包 │
├─────────────────────────────────┤
│                                 │
│  [槽位1]   [槽位2]   [槽位3]    │
│                                 │
│     [槽位4]   [槽位5]           │
│                                 │
├─────────────────────────────────┤
│           操作提示               │
└─────────────────────────────────┘
```

- 有数据的槽位显示为末影箱（紫色），显示摘要信息（主武器/副武器/特殊武器名称、配件数量）
- 空槽位显示为普通箱子（褐色）
- 点击有数据的槽位执行对应操作（加载/保存/删除）

---

## 五、`no_loadout` 标签系统

系统使用 Minecraft 原版 **scoreboard 标签** `no_loadout` 标记玩家的装备状态，可通过原版 `/tag` 指令查看和管理。

### 标签生命周期

| 时机 | 操作 | 说明 |
|------|------|------|
| 打开职业选择 GUI | `player.addTag('no_loadout')` | 标记玩家尚未领取装备 |
| 关闭 GUI（Esc/退出按钮） | `player.removeTag('no_loadout')` | 清除标记 |
| 退出登录 | `player.removeTag('no_loadout')` | 清除标记 |
| 成功发放装备 | `player.removeTag('no_loadout')` | 清除标记 |

### 管理员查询

```mcfunction
# 查看某玩家是否拥有 no_loadout 标签
/tag xkmxz2503 list

# 手动添加/移除标签
/tag xkmxz2503 add no_loadout
/tag xkmxz2503 remove no_loadout

# 查看所有带 no_loadout 标签的玩家
/tag @a[tag=no_loadout] list
```

### 守卫机制

当通过 `/profequip give` 指令发放装备时：

1. **容器守卫** — 若玩家当前有容器打开（职业选择 GUI、箱子、工作台等），则拒绝发放并提示"请先关闭当前打开的界面"
2. **标签守卫** — 若玩家仍有 `no_loadout` 标签（表示尚未完成配置），也拒绝发放

> GUI 内的"给予装备"按钮跳过这两个守卫，因为玩家已在界面内主动操作。

---

## 六、`no_job` 无职业标签系统

系统使用 Minecraft 原版 **scoreboard 标签** `no_job` 标记**未选择职业的玩家**，方便服务器管理员进行权限管理或条件判定（例如限制无职业玩家使用某些功能/区域）。

### 标签生命周期

```
[玩家加入服务器]
    ├─ 已有职业 → 确保 no_job 已移除（防止异常残留）
    └─ 无职业  → 自动添加 no_job 标签 + 提示消息 ←┐
                                                    │
[通过GUI选择职业]                                    │
    ├─ tag add <职业>                               │
    ├─ tag remove no_job ───────────────────────────┘
    └─ 提示"已选择职业"
    
[通过GUI取消职业]
    ├─ tag remove <所有职业>
    ├─ tag add no_job ──────────────────────────→ 回到无职业状态
    └─ 提示"已清除所有选择"
```

| 时机 | 操作 | 说明 |
|------|------|------|
| 玩家登录（无职业） | `player.addTag('no_job')` | 自动标记 + 发送 `msg.kubejs.no_job_tag.added` 提示 |
| 玩家登录（有职业） | 确保 `no_job` 已移除 | 防止标签异常残留 |
| 选择职业后 | `tag remove no_job` | 在设置职业标签后立即移除 |
| 取消职业后 | `tag add no_job` | 在移除所有职业标签后立即添加 |

### 管理员查询

```mcfunction
# 查看某玩家是否拥有 no_job 标签
/tag xkmxz2503 list

# 查看所有无职业的玩家
/tag @a[tag=no_job] list

# 手动添加/移除标签
/tag xkmxz2503 add no_job
/tag xkmxz2503 remove no_job
```

### 实现文件

`server_scripts/profession/no_job_tag.js`：

- `addNoJobTag(player)` — 检查玩家职业，若无职业则添加 `no_job` 标签并提示
- `removeNoJobTag(player)` — 移除玩家的 `no_job` 标签并提示
- `PlayerEvents.loggedIn` — 登录时延迟 1 tick 确保 persistentData 就绪后执行检测

> 提示消息的翻译键定义在 `assets/kubejs/lang/{en_us,zh_cn}.json` 中：
> - `msg.kubejs.no_job_tag.added` — 标记为无职业时提示
> - `msg.kubejs.no_job_tag.removed` — 选择职业后移除标记时提示

---

## 七、指令参考

详细的指令使用说明请参阅 **[指令使用.md](./指令使用.md)**，此处仅作简要说明。

### `/profequip`

装备发放指令，需要 OP 2 级权限。支持控制台/命令方块/数据包/玩家。

```
/profequip give [<targets>]  发放装备（留空给自己，控制台必须指定目标）
/profequip list              查看在线玩家的选择状态
/profequip help              显示帮助
```

### `/kubejsadmin`

管理员清空指令，需要 OP 2 级权限。

```
/kubejsadmin profession <targets>  清空职业选择 + 移除标签
/kubejsadmin menu <targets>        清空队伍配置
```

---

> 队伍选择器是独立模块，详见 **[team-使用教程.md](./team-使用教程.md)**。

---

## 八、语言文件

位置：`assets/kubejs/lang/{en_us,zh_cn}.json`

### 常用翻译键

| 键 | 说明 |
|---|------|
| `gui.kubejs.profession_select.title` | 职业选择页标题 |
| `gui.kubejs.profession_select.subtitle.weapon_config` | 武器配置页副标题 |
| `gui.kubejs.profession_select.hint` | 武器配置页操作提示 |
| `profession.kubejs.assault` | 突击兵名称 |
| `profession.kubejs.assault.desc` | 突击兵描述 |
| `weapon.kubejs.<id>` | 非 TACZ 主武器名称（i18n 用） |
| `offhand.kubejs.<id>` | 非 TACZ 副/特殊武器名称（i18n 用） |
| `msg.kubejs.profession_select.main_weapon` | 选择主武器消息 |
| `msg.kubejs.profession_select.main_cleared` | 清空主武器消息 |
| `gui.kubejs.profession_select.give_equipment` | **给予装备**按钮名称 |
| `gui.kubejs.profession_select.give_equipment.lore` | 给予装备按钮描述 |
| `gui.kubejs.backpack.load_btn` | 加载背包按钮名称 |
| `gui.kubejs.backpack.save_btn` | 保存到背包按钮名称 |
| `gui.kubejs.backpack.delete_btn` | 删除背包按钮名称 |
| `gui.kubejs.attach.save` | 保存配件按钮名称 |
| `gui.kubejs.page.info` | 页码显示（`§7第 %s/%s 页`） |
| `gui.kubejs.page.prev` | 上一页按钮（`§e← 上一页`） |
| `gui.kubejs.page.next` | 下一页按钮（`§e下一页 →`） |
| `msg.kubejs.no_job_tag.added` | 无职业标记提示（`§7[系统] 你尚未选择职业，已标记为无业状态`） |
| `msg.kubejs.no_job_tag.removed` | 移除无职业标记提示（`§a[系统] 已选择职业，无业标记已移除`） |

---

## 九、分页系统

所有列表类型的页面均支持**自动分页**，当物品/选项超过单页容量时自动显示翻页按钮。

### 分页总览

| 页面 | 每页容量 | 布局 | 当前是否需要分页 |
|------|---------|------|----------------|
| **职业选择页** | 7列×3行=21个 | 网格布局 | 当前4职业，无需翻页，未来扩展自动启用 |
| **武器配置页** | 预留分页框架 | 保持原有固定布局 | `pageNum` 已传入，未来可通过 `pageNum` 扩展不同配置页 |
| **主武器列表** | 7列×3行=21个 | 网格布局 | 当前数量少，未来扩展自动启用 |
| **副武器列表** | 7列×3行=21个 | 网格布局 | 同上 |
| **特殊武器列表** | 7列×3行=21个 | 网格布局 | 同上 |
| **配件选择子页面** | 7列×3行=21个 | 固定5行GUI | ✅ **必显**（如 gewehr_1_5 瞄具 30+ 个） |
| **背包选择页** | 3列×2行=6个 | 交错布局 | 当前5槽位不需翻页，`BACKPACK_SLOT_COUNT` 增大后自动启用 |

### 翻页UI布局

所有列表页面的翻页UI风格统一：

```
Row 0: [← 返回] [    ] [    ] [页码X/Y] [    ] [    ] [  ←上一页] [下一页→]
Row 1: [──────────────────── 灰色分隔线 ────────────────────]
Row 2-4: [7列×3行 = 21个物品网格，空位用黑色玻璃板填充]
Row 5: [保持原功能不变]
```

- **页码显示**：居中显示 `第 X/Y 页`（中）/ `Page X/Y`（英）
- **上一页/下一页**：仅在可翻页时显示，使用箭头的物品图标
- **自动隐藏**：当总页数 ≤1 时，整行翻页控件不显示
- **返回按钮**：始终显示在 (0,0)

### 背包装载/保存/删除页UI

```
Row 0: [← 返回] [    ] [    ] [  标题  ] [    ] [    ] [←上一页] [下一页→]
Row 1: [──────────────────── 灰色分隔线 ────────────────────]
Row 2: [槽位1]  [    ] [槽位2]  [    ] [槽位3]
Row 3: [    ]  [槽位4]  [    ] [槽位5]  [    ] [    ] [槽位6]
Row 4: [──────────────────── 灰色分隔线 ────────────────────]
Row 5: [                    操作提示（翻页时显示页码）          ]
```

### 页码传递机制

通过 `page:num` 字符串格式在 `openPage()` 中传递页码：

```js
// 内部调用示例（不需要手动操作）
openPage(player, 'weapon:1')        // 主武器列表第2页
openPage(player, 'offhand:0')       // 副武器列表第1页
openPage(player, 'backpack_save:2') // 背包保存第3页
openPage(player, 'prof:1')          // 职业选择第2页
```

`openPage()` 自动解析 `:` 后的数字，各渲染函数接收 `pageNum` 参数。首次进入某页面时始终从第0页（第1页）开始。

### 配件选择子页面分页

配件选择子页面（`openAttachmentSelect`）从原来的**动态行数布局**改为**固定5行GUI + 分页**：

| 行 | 内容 |
|----|------|
| Row 0 | [返回] [  ] [  ] [页码X/Y] [  ] [  ] [←上一页] [下一页→] |
| Row 1-3 | 7列×3行 = 21个配件网格 |
| Row 4 | 灰色分隔线 |

当配件数量超过21个时（如 gewehr_1_5 瞄具35个），自动分页显示。

### 分页配置参数

```js
// profession_gui.js — 武器/职业列表
var pageSize = 21  // 7列×3行

// a_tacz_config.js — 配件选择
var ATTACH_PAGE_SIZE = 21  // 7列×3行

// profession_backpack.js — 背包槽位
const BACKPACK_PAGE_SIZE = 6  // 3列×2行
```

### 新增内容时的分页行为

无论新增职业、武器还是背包槽位，分页自动生效：

- **新增职业**：在 `PROFESSIONS` 数组追加即可，超过21个时自动翻页
- **新增武器**：在 `weapons.{类别}` 列表追加即可，超过21个时自动翻页
- **新增背包槽位**：修改 `BACKPACK_SLOT_COUNT` 值，超过6个时自动翻页
- **新增配件**：在 `attachments.{槽位}` 数组追加即可，超过21个时自动翻页

---

## 十、新增武器快速指南

### 新增 TACZ 枪械

1. 在 `b_tacz_prof_xxx.js` 的 `guns.{类别}` 中添加枪械配置
2. 在 `weapons.{类别}` 列表中添加武器 ID
3. 无需语言文件（TACZ 自动使用模组名）

### 新增非 TACZ 特殊武器

1. `b_tacz_prof_xxx.js` → `nonTaczDisplay` 添加物品映射 + 名称来源（`i18n` 标记）
2. `b_tacz_prof_xxx.js` → `nonTaczAmmo` 添加弹药（可选）
3. `b_tacz_prof_xxx.js` → `weapons.tertiary` 添加武器 ID
4. 语言文件（仅当需要 KubeJS 覆写名称时）

### 新增职业

1. `profession_gui.js` → `PROFESSIONS` 数组添加职业 ID
2. 新建 `b_tacz_prof_xxx.js` **完整配置**（TACZ枪械 + 非TACZ武器 + 护甲 + 额外物品）
3. `a_tacz_config.js` → `PROF_TAG_LIST` 添加标签
4. 语言文件添加职业名和描述

---

## 十一、注意事项

1. **cleanId**：从 `player.persistentData` 读取的 ID 用于翻译键时，必须用 `cleanId()` 消去引号
2. **文件加载顺序**：`config/` → `prof_configs/b_*.js` → `prof_configs/z_*.js` → 其他
3. **配件改装**：仅 TACZ 枪械支持，非 TACZ 武器右键无配件菜单
4. **弹药发放**：自动走背包优先（9~35号槽），背包满才放快捷栏
5. **修改后刷新**：`/kubejs reload`（脚本）+ `F3+T`（语言文件）
6. **TACZ 配件 ID 必须完整**：`tacz:oem_stock_tactical` 中的 `oem_` 是 OEM 原厂配件的**命名约定**，不是会被剥离的前缀，必须完整写入。写 `tacz:stock_tactical`（缺 `oem_`）是不存在的 ID 且无效。具体每把枪允许哪些配件，查阅 **`tacz所有的枪械配件数据/`** 目录下的枪包数据文件或 **[tacz枪械配件数据-使用指南.md](./tacz枪械配件数据-使用指南.md)**。

---

## 十二、相关文档

| 文档 | 说明 |
|------|------|
| **[使用指南总览](./README.md)** | 所有模块的索引总览 |
| **[队伍选择器](./team-使用教程.md)** | 队伍选择 GUI 使用说明 |
| **[队伍复活券系统](./team_revive-使用教程.md)** | 复活券池管理、死亡消耗淘汰机制 |
| **[指令使用](./指令使用.md)** | `/profequip` `/kubejsadmin` 指令详情 |
| **[TACZ 枪械配件数据](./tacz枪械配件数据-使用指南.md)** | 枪械 GunId、配件、弹药数据查询 |
| **[启动脚本配置](./startup_configs-使用教程.md)** | 物品注册（职业选择器/队伍选择器）、海战平衡、TAOV 参数 |
