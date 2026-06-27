# TACZ 枪械配件数据 - 使用指南

## 目录

- [数据来源](#数据来源)
- [目录结构](#目录结构)
- [文件命名规则](#文件命名规则)
- [数据格式说明](#数据格式说明)
- [如何与职业系统配合使用](#如何与职业系统配合使用)
- [配件槽位对照](#配件槽位对照)
- [弹药对照](#弹药对照)
- [相关文档](#相关文档)

---

## 数据来源

本目录的数据来自服务器安装的所有 TACZ 枪包（Gun Pack），基于各枪包内的 `data/` 目录提取整理而成，用于：

1. **快速查阅**各枪械的 GunId、弹药类型、支持的配件槽位
2. **配置职业武器** — 在 `server_scripts/profession/prof_configs/b_tacz_prof_*.js` 中为各职业配置可用枪械和配件
3. **确保配件 ID 准确** — 避免因配件 ID 拼写错误导致配置无效

---

## 目录结构

```
tacz所有的枪械配件数据/
├── README.md                           ← 全部枪械综合总表（包含所有枪包数据）
├── 00_TACZ默认枪包.md                   ← TACZ 默认枪包（现代枪械）
├── 01_薰衣草枪包_lavender.md            ← 薰衣草枪包（一战主题）
├── 02_启示录枪包_Apocalypse.md          ← 启示录/战地枪包
└── images/                              ← 枪械和配件图片
    ├── tacz_default_gun/
    │   ├── guns/                        ← 枪械图标
    │   └── attachments/                 ← 配件图标
    ├── lavender_converted/
    │   ├── guns/
    │   └── attachments/
    └── apocalypse/
        ├── guns/
        └── attachments/
```

> 各枪包独立文件仅包含本枪包数据，`README.md` 为所有枪包合并后的总表，包含搜索功能所需的全部数据。

---

## 文件命名规则

| 文件 | 枪包名称 | 数据来源 Mod |
|------|---------|-------------|
| `00_TACZ默认枪包.md` | TACZ 默认枪包 | `tacz` |
| `01_薰衣草枪包_lavender.md` | 薰衣草枪包 | `lavender` |
| `02_启示录枪包_Apocalypse.md` | 启示录枪包 | `bf1` |

文件名前缀 `00_`、`01_`、`02_` 仅为排序，无特殊含义。

---

## 数据格式说明

### 枪械总表字段

每条枪械记录包含以下字段：

| 字段 | 说明 | 示例 |
|------|------|------|
| **所属枪包** | 枪械来自哪个枪包 | `TACZ默认枪包` |
| **图片** | 枪械图标（可选） | `<img src="...">` |
| **NBT GunId** | **TACZ 核心标识**，用于 NBT 区分不同枪械 | `tacz:ak47` |
| **枪械名称** | 枪械显示名称 | `AKM 突击步枪` |
| **枪械类型** | 分类（步枪/手枪/冲锋枪/机枪/霰弹枪/狙击枪/重型武器） | `步枪` |
| **弹药ID** | 弹药物品 ID | `tacz:762x39` |
| **弹药名称** | 弹药显示名称 | `7.62x39mm步枪弹` |
| **支持的配件槽位** | 该枪械可安装的配件类型 | `瞄具, 枪托, 枪口, 扩容弹匣` |

### GunId 格式说明

```
<tacz:ak47>          ← TACZ 默认枪包：前缀为 tacz:
<lavender:smle_iii>  ← 薰衣草枪包：前缀为 lavender:
<bf1:mg42>           ← 启示录枪包：前缀为 bf1:
```

GunId 用于 `tacz:modern_kinetic_gun` 物品的 NBT 中：

```js
// 在职业配置中使用的格式
Item.of('tacz:modern_kinetic_gun', {
  custom_data: {
    HasBulletInBarrel: 1b,
    GunId: "tacz:ak47",          // ← 此处填写 GunId
    GunFireMode: "AUTO",
    GunCurrentAmmoCount: 30,
  }
})
```

---

## 如何与职业系统配合使用

职业系统的枪械配置位于 **`server_scripts/profession/prof_configs/b_tacz_prof_xxx.js`**。

详细配置方法请参阅 **[profession-使用教程.md](./profession-使用教程.md)** 的「二、武器类型」和「九、新增武器快速指南」章节。

### 基本步骤

1. **查 GunId**：在本目录的 `.md` 文件中找到目标枪械的 `GunId` 列
2. **查配件**：查看该枪械的「支持的配件槽位」列，了解可用哪些配件类型
3. **查弹药**：查看该枪械的「弹药ID」列，了解所需的弹药类型
4. **写入配置**：在对应的职业配置文件中添加枪械条目

### 示例：在突击兵配置中添加 AK47

```js
// b_tacz_prof_assault.js
PROF_CONFIGS.assault = {
  guns: {
    primary: {
      ak47: {                                 // 武器 ID（自定义标识）
        gunId: 'tacz:ak47',                   // ← 从本目录 GunId 列获取
        GunFireMode: 'AUTO',
        GunCurrentAmmoCount: 30,
        ammo: {
          ammoId: 'tacz:762x39',              // ← 从本目录 弹药ID 列获取
          main: 210,
          level: 2,
        },
        attachments: {
          scope: [{id:'tacz:scope_reflex'}, ...],  // ← 配件 ID 需参考枪包 data/ 确认
          muzzle: [{id:'tacz:muzzle_silencer_knight_qd'}, ...],
          stock: [{id:'tacz:stock_heavy'}, ...],
          extended_mag: [{id:'tacz:extended_mag_1'}, ...],
        },
      },
    },
  },
  weapons: {
    primary: ['ak47'],  // ← guns 中定义的 key
  },
}
```

### 提示

- 配件 ID 可在本目录的枪包数据中找到，但**具体每把枪允许哪些配件**需查阅 `data/tacz/tacz_tags/attachments/allow_attachments/` 下的对应文件
- 配件 ID 必须**完整写入**（如 `tacz:oem_stock_tactical` 中的 `oem_` 不可省略）
- 所有枪械使用 `tacz:modern_kinetic_gun` 作为物品 ID，通过 `custom_data.GunId` 区分

---

## 配件槽位对照

| 配置文件中的 key | 说明 | 物品 ID 前缀示例 |
|-----------------|------|----------------|
| `scope` | 瞄具（红点、全息、倍镜等） | `tacz:sight_*`, `tacz:scope_*` |
| `muzzle` | 枪口（消音器、制退器、刺刀等） | `tacz:muzzle_*`, `tacz:bayonet_*` |
| `stock` | 枪托 | `tacz:stock_*`, `tacz:oem_stock_*` |
| `grip` | 握把 | `tacz:grip_*` |
| `laser` | 激光指示器 | `tacz:laser_*` |
| `extended_mag` | 扩容弹匣 | `tacz:extended_mag_*` |
| `bayonet` | 刺刀（部分枪械专用） | `tacz:bayonet_*` |
| `ammo_mod` | 弹药改装（部分枪械专用） | 因枪包而异 |

---

## 弹药对照

| 弹药 ID | 弹药名称 | 用于枪械示例 |
|---------|---------|-------------|
| `tacz:9mm` | 9mm手枪弹 | MP5、Glock、P90、UZI |
| `tacz:45acp` | .45手枪弹 | P320、M1911、UMP45、Vector |
| `tacz:556x45` | 5.56x45mm步枪弹 | M4A1、SCAR-L、HK416、AUG、M249 |
| `tacz:762x39` | 7.62x39mm步枪弹 | AK47、RPK、SKS |
| `tacz:308` | .308步枪弹 | SCAR-H、MK14、FAL、M249(部分) |
| `tacz:12g` | 12号口径霰弹 | AA12、M870、SPAS-12 |
| `tacz:50ae` | .50 AE手枪弹 | 沙漠之鹰 |
| `tacz:338` | .338狙击弹 | AWP |
| `tacz:50bmg` | .50 BMG狙击弹 | M107、M95 |
| `tacz:30_06` | .30-06步枪弹 | M700 等 |
| `tacz:45_70` | 45-70步枪弹 | 春田等 |
| `tacz:762x54` | 7.62x54mm步枪弹 | MG42、刘易斯等 |
| `tacz:40mm` | 40mm榴弹 | M320、RPG等 |
| `lavender:lebel8x50` | 8x50mm勒贝尔弹 | 勒贝尔、绍沙等 |
| `lavender:british0x303` | .303英式步枪弹 | SMLE、刘易斯等 |
| `lavender:mauser7.92x57` | 7.92x57mm毛瑟弹 | 格韦尔98、MG08/15等 |
| `lavender:griffin9x19` | 9x19mm格里芬弹 | 波莱塔等 |

> 完整弹药列表请查阅各枪包数据文件中的「弹药ID」列。

---

## 相关文档

| 文档 | 说明 |
|------|------|
| **[使用指南总览](./README.md)** | 所有模块的索引总览 |
| **[职业系统](./profession-使用教程.md)** | 职业系统完整配置指南（武器、配件、弹药配置） |
| **[指令使用](./指令使用.md)** | 装备发放和管理指令 |
| **[队伍选择器](./team-使用教程.md)** | 队伍选择器使用说明 |
| **[启动脚本配置](./startup_configs-使用教程.md)** | 物品注册、海战平衡、TAOV 参数 |
| **[队伍复活券系统](./team_revive-使用教程.md)** | 复活券池管理、死亡消耗淘汰机制 |
| **[TACZ 官方枪包文档](https://tacwiki.mcma.club/zh/gunpack/)** | TACZ 枪包格式官方手册 |
